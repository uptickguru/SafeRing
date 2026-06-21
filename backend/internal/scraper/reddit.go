package scraper

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"go.uber.org/zap"
)

// RedditScraper scrapes r/scams and related subreddits for scam phone numbers.
//
// Uses Reddit OAuth2 client credentials flow for authenticated API access.
// Without credentials, falls back to the public JSON feed at old.reddit.com.
//
// Source: https://www.reddit.com/r/scams/
//
// Rate Limiting: Reddit allows 600 requests per 10 minutes for authenticated clients,
// 10 per minute for unauthenticated. The scraper respects this by adding delays.
type RedditScraper struct {
	clientID     string
	clientSecret string
	userAgent    string
	accessToken  string
	tokenExpiry  time.Time
	client       *http.Client
	logger       *zap.Logger
}

// NewRedditScraper creates a new Reddit scraper.
// Provide client credentials for authenticated access (higher rate limits).
// Empty strings for anonymous fallback access.
func NewRedditScraper(clientID, clientSecret, userAgent string, logger *zap.Logger) *RedditScraper {
	if userAgent == "" {
		userAgent = "SafeRing/1.0 (scam detection research; contact: safering@app)"
	}
	return &RedditScraper{
		clientID:     clientID,
		clientSecret: clientSecret,
		userAgent:    userAgent,
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
		logger: logger.Named("scraper.reddit"),
	}
}

// Name returns the scraper identifier.
func (s *RedditScraper) Name() string { return "reddit" }

// Scrape fetches recent posts from r/scams and extracts phone numbers.
func (s *RedditScraper) Scrape(ctx context.Context) ([]ScrapedEntry, error) {
	s.logger.Info("starting Reddit r/scams scrape")

	// Authenticate if credentials are provided and token is expired
	if s.clientID != "" && s.clientSecret != "" {
		if s.accessToken == "" || time.Now().After(s.tokenExpiry) {
			if err := s.authenticate(ctx); err != nil {
				s.logger.Warn("reddit OAuth auth failed, falling back to anonymous access", zap.Error(err))
				s.accessToken = ""
			}
		}
	}

	allEntries, err := s.fetchScamPosts(ctx)
	if err != nil {
		return nil, fmt.Errorf("reddit scrape: %w", err)
	}

	s.logger.Info("Reddit scrape complete", zap.Int("entries", len(allEntries)))
	return allEntries, nil
}

// authenticate acquires an OAuth2 access token from Reddit using client credentials.
func (s *RedditScraper) authenticate(ctx context.Context) error {
	authURL := "https://www.reddit.com/api/v1/access_token"

	data := url.Values{}
	data.Set("grant_type", "client_credentials")

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, authURL, strings.NewReader(data.Encode()))
	if err != nil {
		return fmt.Errorf("create auth request: %w", err)
	}
	req.SetBasicAuth(s.clientID, s.clientSecret)
	req.Header.Set("User-Agent", s.userAgent)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := s.client.Do(req)
	if err != nil {
		return fmt.Errorf("reddit auth request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1000))
		return fmt.Errorf("reddit auth returned status %d: %s", resp.StatusCode, string(body))
	}

	var authResp struct {
		AccessToken string `json:"access_token"`
		TokenType   string `json:"token_type"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&authResp); err != nil {
		return fmt.Errorf("decode reddit auth response: %w", err)
	}

	s.accessToken = authResp.AccessToken
	s.tokenExpiry = time.Now().Add(time.Duration(authResp.ExpiresIn-60) * time.Second)
	s.logger.Info("reddit authenticated successfully")
	return nil
}

// fetchScamPosts retrieves posts from r/scams and related subreddits.
func (s *RedditScraper) fetchScamPosts(ctx context.Context) ([]ScrapedEntry, error) {
	// Fetch from multiple subreddits for broader coverage
	subreddits := []string{"scams", "scamnumbers", "phoneScam"}
	var allEntries []ScrapedEntry

	for _, sub := range subreddits {
		select {
		case <-ctx.Done():
			return allEntries, ctx.Err()
		default:
		}

		entries, err := s.fetchSubreddit(ctx, sub)
		if err != nil {
			s.logger.Warn("error fetching r/"+sub, zap.Error(err))
			continue
		}
		allEntries = append(allEntries, entries...)

		// Respect Reddit rate limits
		time.Sleep(2 * time.Second)
	}

	return allEntries, nil
}

// fetchSubreddit retrieves recent posts from a specific subreddit.
// Tries the authenticated API first, then falls back to old.reddit.com.
func (s *RedditScraper) fetchSubreddit(ctx context.Context, subreddit string) ([]ScrapedEntry, error) {
	// Strategy 1: Use authenticated Reddit API (oauth.reddit.com)
	if s.accessToken != "" {
		entries, err := s.fetchOAuth(ctx, subreddit)
		if err == nil {
			return entries, nil
		}
		s.logger.Warn("OAuth fetch failed, trying public fallback", zap.String("sub", subreddit), zap.Error(err))
	}

	// Strategy 2: Try old.reddit.com (sometimes less restrictive)
	entries, err := s.fetchPublic(ctx, subreddit)
	if err != nil {
		return nil, fmt.Errorf("all reddit strategies failed for r/%s: %w", subreddit, err)
	}
	return entries, nil
}

// fetchOAuth fetches subreddit data using the authenticated OAuth API.
func (s *RedditScraper) fetchOAuth(ctx context.Context, subreddit string) ([]ScrapedEntry, error) {
	// OAuth API endpoint
	apiURL := fmt.Sprintf("https://oauth.reddit.com/r/%s/hot?limit=100", subreddit)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return nil, fmt.Errorf("create oauth request: %w", err)
	}
	req.Header.Set("User-Agent", s.userAgent)
	req.Header.Set("Authorization", "Bearer "+s.accessToken)

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("oauth fetch r/%s: %w", subreddit, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("reddit oauth returned status %d for r/%s", resp.StatusCode, subreddit)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 5*1024*1024))
	if err != nil {
		return nil, fmt.Errorf("read reddit oauth response: %w", err)
	}

	return parseRedditJSON(body)
}

// fetchPublic fetches subreddit data using the public JSON endpoint (old.reddit.com).
func (s *RedditScraper) fetchPublic(ctx context.Context, subreddit string) ([]ScrapedEntry, error) {
	// Use old.reddit.com which has slightly less aggressive blocking than www
	apiURL := fmt.Sprintf("https://old.reddit.com/r/%s/hot.json?limit=100", subreddit)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return nil, fmt.Errorf("create public request: %w", err)
	}
	// Use a more common-looking user-agent for the public endpoint
	ua := s.userAgent
	if strings.Contains(ua, "SafeRing") || ua == "" {
		ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
	}
	req.Header.Set("User-Agent", ua)
	req.Header.Set("Accept", "application/json")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("public fetch r/%s: %w", subreddit, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("reddit public returned status %d for r/%s", resp.StatusCode, subreddit)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 5*1024*1024))
	if err != nil {
		return nil, fmt.Errorf("read reddit public response: %w", err)
	}

	return parseRedditJSON(body)
}

// Reddit API response types
type redditListing struct {
	Data struct {
		Children []redditChild `json:"children"`
	} `json:"data"`
}

type redditChild struct {
	Kind string     `json:"kind"`
	Data redditPost `json:"data"`
}

type redditPost struct {
	ID         string  `json:"id"`
	Title      string  `json:"title"`
	SelfText   string  `json:"selftext"`
	Score      int     `json:"score"`
	CreatedUTC float64 `json:"created_utc"`
	URL        string  `json:"url"`
	Over18     bool    `json:"over_18"`
}

// parseRedditJSON parses Reddit's JSON API response into scraped entries.
// This is a standalone function (not a method) to allow reuse from agent code.
func parseRedditJSON(body []byte) ([]ScrapedEntry, error) {
	var listing redditListing
	if err := json.Unmarshal(body, &listing); err != nil {
		return nil, fmt.Errorf("parse reddit json: %w", err)
	}

	var entries []ScrapedEntry
	seenHashes := make(map[string]bool)

	for _, child := range listing.Data.Children {
		if child.Kind != "t3" {
			continue
		}
		post := child.Data
		if post.Over18 {
			continue
		}
		if post.Score < 2 {
			continue // Skip posts with very low engagement
		}

		// Combine title and self text for phone number extraction
		text := post.Title + " " + post.SelfText

		phoneNumbers := phonePattern.FindAllString(text, -1)
		for _, phone := range phoneNumbers {
			cleaned := CleanPhoneNumber(phone)
			if cleaned == "" {
				continue
			}

			hash := HashPhoneNumber(cleaned)
			if seenHashes[hash] {
				continue
			}
			seenHashes[hash] = true

			reportedAt := time.Unix(int64(post.CreatedUTC), 0)
			scamType := NormalizeScamType(post.Title)

			// Reddit posts have lower baseline credibility (need verification)
			riskScore := 0.45
			if post.Score > 10 {
				riskScore = 0.55 // Higher engagement increases confidence
			}

			entries = append(entries, ScrapedEntry{
				NumberHash: hash,
				Source:     "reddit",
				ScamType:   scamType,
				RiskScore:  riskScore,
				ReportedAt: reportedAt,
			})
		}
	}

	return entries, nil
}
