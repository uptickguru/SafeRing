package scraper

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"go.uber.org/zap"
)

// TwitterScraper scrapes X/Twitter for scam phone numbers via the v2 API.
// Requires a Twitter API Bearer Token (free tier: 1500 tweets/month).
type TwitterScraper struct {
	bearerToken string
	client      *http.Client
	logger      *zap.Logger
}

// NewTwitterScraper creates a Twitter/X scraper.
func NewTwitterScraper(bearerToken string, logger *zap.Logger) *TwitterScraper {
	return &TwitterScraper{
		bearerToken: bearerToken,
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
		logger: logger.Named("scraper.twitter"),
	}
}

// Name returns the scraper identifier.
func (s *TwitterScraper) Name() string { return "x-twitter" }

// Scrape searches Twitter for recent tweets mentioning scam phone numbers.
func (s *TwitterScraper) Scrape(ctx context.Context) ([]ScrapedEntry, error) {
	if s.bearerToken == "" {
		s.logger.Warn("no Twitter bearer token configured, skipping")
		return nil, nil
	}

	queries := []string{
		"(scam OR scammer) (phone OR number OR call) -is:retweet lang:en",
		"(scam call) (number) -is:retweet lang:en",
		"\"scam number\" OR \"scammer called\" -is:retweet lang:en",
	}

	var allEntries []ScrapedEntry
	seen := make(map[string]bool)

	for _, q := range queries {
		select {
		case <-ctx.Done():
			return allEntries, ctx.Err()
		default:
		}

		entries, err := s.searchQuery(ctx, q)
		if err != nil {
			s.logger.Warn("twitter search failed", zap.String("query", q[:30]), zap.Error(err))
			continue
		}

		for _, e := range entries {
			if !seen[e.NumberHash] {
				seen[e.NumberHash] = true
				allEntries = append(allEntries, e)
			}
		}

		time.Sleep(2 * time.Second) // Rate limit politeness
	}

	return allEntries, nil
}

func (s *TwitterScraper) searchQuery(ctx context.Context, query string) ([]ScrapedEntry, error) {
	url := fmt.Sprintf("https://api.twitter.com/2/tweets/search/recent?query=%s&max_results=100&tweet.fields=created_at,text",
		urlQueryEscape(query))

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+s.bearerToken)

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1000))
		return nil, fmt.Errorf("twitter API returned %d: %s", resp.StatusCode, string(body))
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 2*1024*1024))
	if err != nil {
		return nil, err
	}

	return s.parseResponse(body)
}

type twitterResponse struct {
	Data []struct {
		ID        string `json:"id"`
		Text      string `json:"text"`
		CreatedAt string `json:"created_at"`
	} `json:"data"`
	Meta struct {
		ResultCount int `json:"result_count"`
	} `json:"meta"`
}

func (s *TwitterScraper) parseResponse(body []byte) ([]ScrapedEntry, error) {
	var resp twitterResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("parse twitter response: %w", err)
	}

	var entries []ScrapedEntry
	for _, tweet := range resp.Data {
		phones := phonePattern.FindAllString(tweet.Text, -1)
		for _, phone := range phones {
			cleaned := CleanPhoneNumber(phone)
			if cleaned == "" {
				continue
			}

			hash := HashPhoneNumber(cleaned)
			scamType := NormalizeScamType(tweet.Text)

			var reportedAt time.Time
			if tweet.CreatedAt != "" {
				reportedAt, _ = time.Parse(time.RFC3339, tweet.CreatedAt)
			}
			if reportedAt.IsZero() {
				reportedAt = time.Now()
			}

			entries = append(entries, ScrapedEntry{
				NumberHash: hash,
				Source:     "x-twitter",
				ScamType:   scamType,
				RiskScore:  0.5,
				ReportedAt: reportedAt,
			})
		}
	}

	return entries, nil
}

// urlQueryEscape is a minimal URL query string encoder.
func urlQueryEscape(s string) string {
	result := make([]byte, 0, len(s)*3)
	for i := 0; i < len(s); i++ {
		c := s[i]
		if (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
			c == '-' || c == '_' || c == '.' || c == '~' {
			result = append(result, c)
		} else if c == ' ' {
			result = append(result, '%', '2', '0')
		} else if c == '(' || c == ')' || c == '"' {
			result = append(result, '%')
			result = append(result, fmt.Sprintf("%02X", c)...)
		} else {
			result = append(result, '%')
			result = append(result, fmt.Sprintf("%02X", c)...)
		}
	}
	return string(result)
}
