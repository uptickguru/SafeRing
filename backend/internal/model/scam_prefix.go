package model

import "time"

// ScamPrefix represents a phone number prefix (area code + central office code)
// that is associated with scam activity. Prefixes allow clients to flag entire
// ranges of numbers used by robocallers and scam operations without needing
// to check every individual number.
//
// Example: prefix "1213555" means +1 (213) 555-xxxx is a known scam block.
type ScamPrefix struct {
	// ID is the auto-increment primary key.
	ID int64 `json:"-" db:"id"`

	// Prefix is the hashed prefix string.
	// Typically the first 7-10 digits of a number in E.164 format.
	Prefix string `json:"prefix" db:"prefix"`

	// CountryCode is the international dialing code (e.g., "1" for US/CA).
	CountryCode string `json:"country_code,omitempty" db:"country_code"`

	// RiskScore is the aggregate risk for numbers under this prefix.
	RiskScore float64 `json:"risk_score" db:"risk_score"`

	// Risk is iOS-compatible alias for RiskScore
	Risk float64 `json:"risk" db:"-"`

	// ScamType is the predominant scam type for this prefix.
	ScamType string `json:"scam_type,omitempty" db:"scam_type"`

	// ReportCount is the number of reports associated with this prefix.
	ReportCount int `json:"report_count,omitempty" db:"report_count"`

	// Count is iOS-compatible alias for SamplesHashed
	Count int `json:"count,omitempty" db:"-"`

	// SamplesHashed is the count of individual hashed numbers in this prefix.
	SamplesHashed int `json:"samples_hashed,omitempty" db:"samples_hashed"`

	// CommonTags is iOS-compatible alias for ScamType as array
	CommonTags []string `json:"common_tags,omitempty" db:"-"`

	// LastUpdated is when this prefix record was last refreshed.
	LastUpdated time.Time `json:"last_updated,omitempty" db:"last_updated"`
}

// PrefixesResponse is the JSON response for the GET /v1/prefixes endpoint.
type PrefixesResponse struct {
	Prefixes []ScamPrefix `json:"prefixes"`
	Total    int          `json:"total"`
	Updated  time.Time    `json:"last_updated"`
	// iOS-compatible: unix timestamp for updated_at
	UpdatedAt int64 `json:"updated_at"`
}
