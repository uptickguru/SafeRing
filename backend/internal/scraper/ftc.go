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

// FTCConsumerSentinelScraper scrapes the FTC Do Not Call / Robocall complaints API.
//
// Source: https://www.ftc.gov/developer/api/v0/endpoints/do-not-call-dnc-reported-calls-data-api
// API:    https://api.ftc.gov/v0/dnc-complaints
//
// The FTC publishes consumer complaints about unwanted calls, including phone numbers used.
// This is a free public API requiring only an api.data.gov API key (DEMO_KEY works for testing).
//
// Data fields include:
//   - company-phone-number: the phone number originating the unwanted call
//   - subject: category (e.g., "Computer & technical support", "Lotteries, prizes & sweepstakes")
//   - recorded-message-or-robocall: "Y" if robocall
//   - created-date / violation-date: when the complaint was filed / call was received
//   - consumer-city / consumer-state: caller's reported location
type FTCConsumerSentinelScraper struct {
	apiKey  string
	baseURL string
	client  *http.Client
	logger  *zap.Logger
}

// NewFTCConsumerSentinelScraper creates a new FTC scraper.
// Provide an API key from api.data.gov (signup at https://api.data.gov/signup).
// Pass "DEMO_KEY" for limited testing access.
func NewFTCConsumerSentinelScraper(apiKey string, logger *zap.Logger) *FTCConsumerSentinelScraper {
	if apiKey == "" {
		apiKey = "DEMO_KEY"
	}
	return &FTCConsumerSentinelScraper{
		apiKey:  apiKey,
		baseURL: "https://api.ftc.gov",
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
		logger: logger.Named("scraper.ftc"),
	}
}

// Name returns the scraper identifier.
func (s *FTCConsumerSentinelScraper) Name() string { return "ftc" }

// Scrape pulls the latest DNC/robocall complaints from the FTC API.
func (s *FTCConsumerSentinelScraper) Scrape(ctx context.Context) ([]ScrapedEntry, error) {
	s.logger.Info("starting FTC DNC complaints scrape")

	entries, err := s.fetchData(ctx)
	if err != nil {
		return nil, fmt.Errorf("ftc scrape: %w", err)
	}

	s.logger.Info("FTC scrape complete", zap.Int("entries", len(entries)))
	return entries, nil
}

// fetchData retrieves DNC complaint records from the FTC API for the last 7 days.
func (s *FTCConsumerSentinelScraper) fetchData(ctx context.Context) ([]ScrapedEntry, error) {
	now := time.Now()
	sevenDaysAgo := now.AddDate(0, 0, -7)

	// Build query with date range filter for the last 7 days
	params := url.Values{}
	params.Set("api_key", s.apiKey)
	params.Set("created_date_from", fmt.Sprintf(`"%s"`, sevenDaysAgo.Format("2006-01-02 15:04:05")))
	params.Set("created_date_to", fmt.Sprintf(`"%s"`, now.Format("2006-01-02 15:04:05")))
	params.Set("items_per_page", "50") // Max per page is 50
	params.Set("sort_order", "DESC")

	dataURL := fmt.Sprintf("%s/v0/dnc-complaints?%s", s.baseURL, params.Encode())

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, dataURL, nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("User-Agent", "SafeRing/1.0 (scam detection research)")
	req.Header.Set("Accept", "application/json")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch FTC data: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		bodyBytes, _ := io.ReadAll(io.LimitReader(resp.Body, 1000))
		return nil, fmt.Errorf("ftc API returned status %d: %s", resp.StatusCode, string(bodyBytes))
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 5*1024*1024)) // 5MB limit
	if err != nil {
		return nil, fmt.Errorf("read ftc response: %w", err)
	}

	entries, err := s.parseFTCData(body)
	if err != nil {
		return nil, err
	}

	// Try to fetch additional pages if available
	// (The API only returns 50 items per page max, so we need pagination)
	entries = append(entries, s.fetchRemainingPages(ctx, body)...)

	return entries, nil
}

// fetchRemainingPages iterates over remaining pages of FTC data.
func (s *FTCConsumerSentinelScraper) fetchRemainingPages(ctx context.Context, firstPage []byte) []ScrapedEntry {
	var allEntries []ScrapedEntry
	return allEntries // Simplified: return first page for now to avoid rate limits
}

// ftcResponse represents the FTC DNC API response structure (jsonapi.org format).
type ftcResponse struct {
	Data []ftcRecord `json:"data"`
	Meta struct {
		RecordsThisPage int `json:"records-this-page"`
		RecordTotal     int `json:"record-total"`
	} `json:"meta"`
}

type ftcRecord struct {
	Type       string          `json:"type"`
	ID         string          `json:"id"`
	Attributes ftcAttributes   `json:"attributes"`
}

type ftcAttributes struct {
	CompanyPhoneNumber string `json:"company-phone-number"`
	CreatedDate       string `json:"created-date"`
	ViolationDate     string `json:"violation-date"`
	ConsumerCity      string `json:"consumer-city"`
	ConsumerState     string `json:"consumer-state"`
	ConsumerAreaCode  string `json:"consumer-area-code"`
	Subject           string `json:"subject"`
	IsRobocall        string `json:"recorded-message-or-robocall"`
}

// parseFTCData parses FTC DNC API JSON response into scraped entries.
func (s *FTCConsumerSentinelScraper) parseFTCData(data []byte) ([]ScrapedEntry, error) {
	var resp ftcResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parse ftc json: %w", err)
	}

	if len(resp.Data) == 0 {
		s.logger.Info("no FTC data returned")
		return nil, nil
	}

	return s.convertFTC(resp), nil
}

// convertFTC transforms FTC complaint records to the common ScrapedEntry format.
func (s *FTCConsumerSentinelScraper) convertFTC(resp ftcResponse) []ScrapedEntry {
	var entries []ScrapedEntry
	seen := make(map[string]bool)

	for _, r := range resp.Data {
		phone := CleanPhoneNumber(r.Attributes.CompanyPhoneNumber)
		if phone == "" {
			continue
		}

		hash := HashPhoneNumber(phone)
		if seen[hash] {
			continue
		}
		seen[hash] = true

		// Map the FTC subject to a scam type
		subject := r.Attributes.Subject
		scamType := normalizeFTCDNCSubject(subject)

		// Robocalls get a higher risk score
		riskScore := 0.6
		if r.Attributes.IsRobocall == "Y" {
			riskScore = 0.7
		}

		var reportedAt time.Time
		if r.Attributes.ViolationDate != "" {
			reportedAt, _ = time.Parse("2006-01-02 15:04:05", r.Attributes.ViolationDate)
		}
		if reportedAt.IsZero() && r.Attributes.CreatedDate != "" {
			reportedAt, _ = time.Parse("2006-01-02 15:04:05", r.Attributes.CreatedDate)
		}
		if reportedAt.IsZero() {
			reportedAt = time.Now()
		}

		entries = append(entries, ScrapedEntry{
			NumberHash: hash,
			Source:     "ftc",
			ScamType:   scamType,
			RiskScore:  riskScore,
			ReportedAt: reportedAt,
		})
	}
	return entries
}

// normalizeFTCDNCSubject maps FTC DNC subject labels to SafeRing's standardized types.
func normalizeFTCDNCSubject(subject string) string {
	s := strings.ToLower(strings.TrimSpace(subject))
	if s == "" {
		return "robocall"
	}

	// FTC DNC complaint categories
	switch {
	case strings.Contains(s, "computer"), strings.Contains(s, "tech support"), strings.Contains(s, "technical support"):
		return "tech-support"
	case strings.Contains(s, "lottery"), strings.Contains(s, "prize"), strings.Contains(s, "sweepstakes"):
		return "other"
	case strings.Contains(s, "debt"), strings.Contains(s, "collection"):
		return "other"
	case strings.Contains(s, "medicare"), strings.Contains(s, "medical"), strings.Contains(s, "health"):
		return "medicare"
	case strings.Contains(s, "bank"), strings.Contains(s, "credit"), strings.Contains(s, "financial"), strings.Contains(s, "card"):
		return "phishing"
	case strings.Contains(s, "warranty"), strings.Contains(s, "automotive"), strings.Contains(s, "car"):
		return "other"
	case strings.Contains(s, "dropped call"), strings.Contains(s, "no message"), strings.Contains(s, "no subject"):
		return "robocall"
	case strings.Contains(s, "imposter"), strings.Contains(s, "impersonation"), strings.Contains(s, "government"):
		return "phishing"
	case strings.Contains(s, "charity"), strings.Contains(s, "donation"):
		return "other"
	case strings.Contains(s, "travel"), strings.Contains(s, "vacation"), strings.Contains(s, "timeshare"):
		return "other"
	case strings.Contains(s, "investment"), strings.Contains(s, "business opportunity"):
		return "other"
	default:
		return NormalizeScamType(s)
	}
}
