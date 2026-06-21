package scraper

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"regexp"
	"strings"
	"time"
)

// ScrapedEntry is the canonical output format for all scrapers.
// It represents a single identified scam phone number from any source.
//
// Zero PII: NumberHash is SHA-256("1" + e164_number), never the original number.
type ScrapedEntry struct {
	NumberHash string    `json:"hash"`
	Source     string    `json:"source"`
	ScamType   string    `json:"scam_type,omitempty"`
	RiskScore  float64   `json:"risk_score"`
	ReportedAt time.Time `json:"reported_at,omitempty"`
}

// Scraper defines the interface that every data source scraper must implement.
type Scraper interface {
	// Name returns a human-readable identifier for this scraper.
	Name() string

	// Scrape fetches and parses data from the source, returning entries.
	// The context supports cancellation and timeouts.
	Scrape(ctx context.Context) ([]ScrapedEntry, error)
}

// phoneCleaner is a compiled regex for stripping non-digit characters.
var nonDigitRE = regexp.MustCompile(`[^\d]`)

// phonePattern matches common phone number formats:
// +1 (555) 123-4567, 555-123-4567, 15551234567, etc.
var phonePattern = regexp.MustCompile(`(\+?1?[\s.-]?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4})`)

// CleanPhoneNumber strips formatting from a phone number, returning E.164 format.
// Returns empty string if the number is invalid (< 10 digits).
func CleanPhoneNumber(s string) string {
	cleaned := nonDigitRE.ReplaceAllString(s, "")
	if len(cleaned) < 10 {
		return ""
	}
	// Normalize to E.164 with leading country code (defaults to 1 for US/CA)
	if len(cleaned) == 10 {
		cleaned = "1" + cleaned
	} else if len(cleaned) == 11 && cleaned[0] != '1' {
		cleaned = "1" + cleaned[:10]
	}
	return cleaned
}

// HashPhoneNumber produces SHA-256("1" + e164_number) as a hex string.
// This is the core privacy guarantee: one-way irreversible hash.
func HashPhoneNumber(phone string) string {
	h := sha256.Sum256([]byte(phone))
	return hex.EncodeToString(h[:])
}

// NormalizeScamType maps source-specific scam type labels to SafeRing's standardized types.
var scamTypeMappings = map[string]string{
	"irs":             "irs",
	"tax":             "irs",
	"irs impersonation": "irs",
	"tax refund":      "irs",
	"tech support":    "tech-support",
	"computer support": "tech-support",
	"virus":           "tech-support",
	"grandparent":     "grandparent",
	"grandparent scam": "grandparent",
	"family emergency": "grandparent",
	"romance":         "romance",
	"dating":          "romance",
	"phishing":        "phishing",
	"bank":            "phishing",
	"account":         "phishing",
	"medicare":        "medicare",
	"medicaid":        "medicare",
	"health insurance": "medicare",
	"robocall":        "robocall",
	"robo call":       "robocall",
	"auto dialer":     "robocall",
	"spoofed":         "spoofed",
	"spoof":           "spoofed",
	"caller id spoof": "spoofed",
	"debt collection": "other",
	"lottery":         "other",
	"sweepstakes":     "other",
	"charity":         "other",
	"warranty":        "other",
	"refund":          "other",
	"other":           "other",
}

// NormalizeScamType maps a source-specific label to a standard tag.
// Returns "other" if no mapping is found.
func NormalizeScamType(label string) string {
	label = strings.ToLower(strings.TrimSpace(label))
	if mapped, ok := scamTypeMappings[label]; ok {
		return mapped
	}
	// Partial match fallback
	for key, val := range scamTypeMappings {
		if strings.Contains(label, key) {
			return val
		}
	}
	return "other"
}
