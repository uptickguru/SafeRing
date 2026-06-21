package scraper

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"time"

	"go.uber.org/zap"
	"golang.org/x/net/html"
)

// BBBScamTrackerScraper scrapes the BBB Scam Tracker for reported scam phone numbers.
//
// Primary source: https://www.bbb.org/scamtracker/lookupscam
// Individual scam reports: https://www.bbb.org/scamtracker/lookupscam/{id}
//
// The BBB publishes consumer-reported scams. Each report may include a scammer phone number,
// scam type, description, and location. This scraper:
//   1. Fetches the main lookupscam page for recent scam IDs
//   2. Fetches each scam detail page for structured data including scammer phone numbers
//   3. Extracts phone numbers from descriptions as a fallback
type BBBScamTrackerScraper struct {
	baseURL string
	client  *http.Client
	logger  *zap.Logger
}

// NewBBBScamTrackerScraper creates a new BBB Scam Tracker scraper.
func NewBBBScamTrackerScraper(logger *zap.Logger) *BBBScamTrackerScraper {
	return &BBBScamTrackerScraper{
		baseURL: "https://www.bbb.org",
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
		logger: logger.Named("scraper.bbb"),
	}
}

// Name returns the scraper identifier.
func (s *BBBScamTrackerScraper) Name() string { return "bbb" }

// Scrape pulls the latest scam reports from BBB Scam Tracker.
// Handles pagination to collect recent entries.
func (s *BBBScamTrackerScraper) Scrape(ctx context.Context) ([]ScrapedEntry, error) {
	s.logger.Info("starting BBB Scam Tracker scrape")

	allEntries, err := s.fetchAllPages(ctx)
	if err != nil {
		return nil, fmt.Errorf("bbb scrape: %w", err)
	}

	s.logger.Info("BBB scrape complete", zap.Int("entries", len(allEntries)))
	return allEntries, nil
}

// fetchAllPages fetches the main lookupscam page, extracts scam IDs, then fetches details.
func (s *BBBScamTrackerScraper) fetchAllPages(ctx context.Context) ([]ScrapedEntry, error) {
	var allEntries []ScrapedEntry
	seenIDs := make(map[string]bool)

	// Step 1: Get scam IDs from the main lookupscam page
	pageURL := fmt.Sprintf("%s/scamtracker/lookupscam", s.baseURL)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, pageURL, nil)
	if err != nil {
		return nil, fmt.Errorf("create bbb list request: %w", err)
	}
	req.Header.Set("User-Agent", "SafeRing/1.0 (scam detection research)")
	req.Header.Set("Accept", "text/html")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch bbb list: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("bbb returned status %d for list page", resp.StatusCode)
	}

	// Parse the HTML to extract scam IDs
	scamIDs := s.extractScamIDs(resp.Body)

	s.logger.Info("extracted BBB scam IDs", zap.Int("count", len(scamIDs)))

	// Step 2: Fetch details for each scam ID (limit to first 10 to avoid rate limiting)
	maxDetails := 10
	if len(scamIDs) < maxDetails {
		maxDetails = len(scamIDs)
	}

	for i := 0; i < maxDetails; i++ {
		select {
		case <-ctx.Done():
			return allEntries, ctx.Err()
		default:
		}

		id := scamIDs[i]
		if seenIDs[id] {
			continue
		}
		seenIDs[id] = true

		entries, err := s.fetchScamDetail(ctx, id)
		if err != nil {
			s.logger.Warn("error fetching BBB scam detail", zap.String("id", id), zap.Error(err))
			// Also try extracting phone numbers from the listing page data as fallback
			continue
		}

		allEntries = append(allEntries, entries...)

		// Rate limit: be polite to BBB's servers
		time.Sleep(1 * time.Second)
	}

	return allEntries, nil
}

// extractScamIDs parses the BBB lookupscam HTML page to find scam report IDs.
func (s *BBBScamTrackerScraper) extractScamIDs(r io.Reader) []string {
	var ids []string
	seen := make(map[string]bool)

	// Pattern: /scamtracker/lookupscam/{number}
	idPattern := regexp.MustCompile(`/scamtracker/lookupscam/(\d+)`)

	z := html.NewTokenizer(r)
	for {
		tt := z.Next()
		if tt == html.ErrorToken {
			break
		}
		if tt == html.StartTagToken || tt == html.SelfClosingTagToken {
			tn, hasAttr := z.TagName()
			if string(tn) == "a" && hasAttr {
				for {
					key, val, more := z.TagAttr()
					if string(key) == "href" {
						matches := idPattern.FindStringSubmatch(string(val))
						if len(matches) > 1 {
							id := matches[1]
							if !seen[id] {
								seen[id] = true
								ids = append(ids, id)
							}
						}
					}
					if !more {
						break
					}
				}
			}
		}
	}

	return ids
}

// bbbScamReport is the structure of scam data embedded in BBB detail pages.
type bbbScamReport struct {
	ScammerID   int     `json:"scammer_id"`
	Description string  `json:"description"`
	CreatedOn   string  `json:"createdOn"`
	ModifiedOn  string  `json:"modifiedOn"`
	ScamName    string  `json:"scam_name"`
	ScamType    string  `json:"scam_type"`
	ScammerPhone string `json:"scammer_phone"`
	ScammerEmail string `json:"scammer_email"`
	DollarValue float64 `json:"dollar_value"`
	Confirmed   bool    `json:"confirmed"`
	Definition  string  `json:"definition"`
}

// fetchScamDetail fetches an individual BBB scam report page and extracts the structured data.
func (s *BBBScamTrackerScraper) fetchScamDetail(ctx context.Context, scamID string) ([]ScrapedEntry, error) {
	detailURL := fmt.Sprintf("%s/scamtracker/lookupscam/%s", s.baseURL, scamID)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, detailURL, nil)
	if err != nil {
		return nil, fmt.Errorf("create bbb detail request: %w", err)
	}
	req.Header.Set("User-Agent", "SafeRing/1.0 (scam detection research)")
	req.Header.Set("Accept", "text/html")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch bbb detail %s: %w", scamID, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("bbb returned status %d for detail %s", resp.StatusCode, scamID)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 2*1024*1024)) // 2MB limit
	if err != nil {
		return nil, fmt.Errorf("read bbb detail response: %w", err)
	}

	return s.parseBBBDetail(body)
}

// scamDataPattern matches the embedded JSON scam data in BBB detail pages.
// The data appears as: scammer_id":<number>,"description":"...","createdOn":...
var scamDataPattern = regexp.MustCompile(`scammer_id[^}]+}`)

// parseBBBDetail extracts the embedded scam data JSON from BBB detail page HTML.
func (s *BBBScamTrackerScraper) parseBBBDetail(body []byte) ([]ScrapedEntry, error) {
	// Find the embedded JSON data
	match := scamDataPattern.Find(body)
	if match == nil {
		// Try extracting phone numbers from the page text as fallback
		return s.extractPhonesFromHTML(body)
	}

	// Clean up the match to be valid JSON
	jsonStr := "{" + string(match) + "}"
	// Unescape any escaped quotes
	jsonStr = strings.ReplaceAll(jsonStr, `\"`, `"`)

	var report bbbScamReport
	if err := json.Unmarshal([]byte(jsonStr), &report); err != nil {
		s.logger.Warn("failed to parse BBB scam JSON", zap.Error(err))
		return s.extractPhonesFromHTML(body)
	}

	return s.convertBBBReport(report, body), nil
}

// extractPhonesFromHTML extracts phone numbers from the raw HTML body as a fallback.
func (s *BBBScamTrackerScraper) extractPhonesFromHTML(body []byte) ([]ScrapedEntry, error) {
	phones := phonePattern.FindAllString(string(body), -1)
	var entries []ScrapedEntry
	seen := make(map[string]bool)

	for _, phone := range phones {
		cleaned := CleanPhoneNumber(phone)
		if cleaned == "" {
			continue
		}
		hash := HashPhoneNumber(cleaned)
		if seen[hash] {
			continue
		}
		seen[hash] = true

		entries = append(entries, ScrapedEntry{
			NumberHash: hash,
			Source:     "bbb",
			ScamType:   "other",
			RiskScore:  0.6,
			ReportedAt: time.Now(),
		})
	}

	return entries, nil
}

// convertBBBReport transforms a BBB scam report into ScrapedEntry format.
func (s *BBBScamTrackerScraper) convertBBBReport(report bbbScamReport, body []byte) []ScrapedEntry {
	var entries []ScrapedEntry
	seen := make(map[string]bool)

	// Primary: use the scammer_phone field
	if report.ScammerPhone != "" {
		phone := CleanPhoneNumber(report.ScammerPhone)
		if phone != "" {
			hash := HashPhoneNumber(phone)
			seen[hash] = true

			scamType := NormalizeScamType(report.ScamType)
			if scamType == "other" {
				scamType = NormalizeScamType(report.ScamName)
			}
			if scamType == "other" {
				scamType = NormalizeScamType(report.Description)
			}

			var reportedAt time.Time
			if report.CreatedOn != "" {
				reportedAt, _ = time.Parse("2006-01-02", report.CreatedOn)
			}
			if reportedAt.IsZero() {
				reportedAt = time.Now()
			}

			riskScore := 0.7 // BBB reports have high credibility
			if !report.Confirmed {
				riskScore = 0.5
			}

			entries = append(entries, ScrapedEntry{
				NumberHash: hash,
				Source:     "bbb",
				ScamType:   scamType,
				RiskScore:  riskScore,
				ReportedAt: reportedAt,
			})
		}
	}

	// Secondary: extract any phone numbers from the description or body
	phones := phonePattern.FindAllString(report.Description+" "+string(body), -1)
	for _, phone := range phones {
		cleaned := CleanPhoneNumber(phone)
		if cleaned == "" {
			continue
		}
		hash := HashPhoneNumber(cleaned)
		if seen[hash] {
			continue
		}
		seen[hash] = true

		scamType := NormalizeScamType(report.ScamType)
		if scamType == "other" {
			scamType = NormalizeScamType(report.Description)
		}

		var reportedAt time.Time
		if report.CreatedOn != "" {
			reportedAt, _ = time.Parse("2006-01-02", report.CreatedOn)
		}
		if reportedAt.IsZero() {
			reportedAt = time.Now()
		}

		entries = append(entries, ScrapedEntry{
			NumberHash: hash,
			Source:     "bbb",
			ScamType:   scamType,
			RiskScore:  0.6,
			ReportedAt: reportedAt,
		})
	}

	return entries
}
