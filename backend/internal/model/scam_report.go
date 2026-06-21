package model

import "time"

// ScamReport represents a user-submitted scam report.
//
// Zero PII Guarantee:
// - NumberHash is SHA-256("1" + e164_number) — the original number is never received.
// - No IP address or device identifier is stored alongside the report.
// - The Tag field is restricted to a predefined set to avoid free-text PII leaks.
type ScamReport struct {
	// ID is the auto-increment primary key.
	ID int64 `json:"-" db:"id"`

	// NumberHash is the SHA-256 hash of the reported number.
	NumberHash string `json:"hash" db:"number_hash"`

	// Tag is an optional classification tag from a predefined set.
	// Must be one of: "irs", "tech-support", "grandparent", "romance",
	// "phishing", "robocall", "spoofed", "medicare", "other".
	Tag string `json:"tag,omitempty" db:"tag"`

	// ReportedAt is when the user submitted the report.
	ReportedAt time.Time `json:"reported_at" db:"reported_at"`

	// Source is always "user_report" for user-submitted reports.
	Source string `json:"-" db:"source"`
}

// ReportRequest is the JSON body for POST /v1/report.
type ReportRequest struct {
	NumberHash string `json:"hash"`
	Tag        string `json:"tag,omitempty"`
	Timestamp  string `json:"timestamp,omitempty"` // ISO 8601, optional
}

// Validate checks that the report request is well-formed.
// Returns a user-friendly error message if validation fails.
func (r *ReportRequest) Validate() error {
	if r.NumberHash == "" {
		return ErrMissingHash
	}
	if len(r.NumberHash) != 64 {
		return ErrInvalidHash
	}
	// Validate hex characters
	for _, c := range r.NumberHash {
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
			return ErrInvalidHash
		}
	}
	if r.Tag != "" && !isValidTag(r.Tag) {
		return ErrInvalidTag
	}
	return nil
}

// Valid tags for scam reports.
var validTags = map[string]bool{
	"irs":           true,
	"tech-support":  true,
	"grandparent":   true,
	"romance":       true,
	"phishing":      true,
	"robocall":      true,
	"spoofed":       true,
	"medicare":      true,
	"other":         true,
}

func isValidTag(tag string) bool {
	return validTags[tag]
}
