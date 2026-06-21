// Package model defines the core data types for the SafeRing backend.
package model

import "time"

// ScamNumber represents a known scam phone number (stored as SHA-256 hash only).
//
// Zero PII Guarantee:
// - NumberHash contains only hex-encoded SHA-256("1" + number)
// - Original phone numbers are never stored or logged
// - The hash is one-way: cannot be reversed to the original number
type ScamNumber struct {
	// ID is the auto-increment primary key.
	ID int64 `json:"-" db:"id"`

	// NumberHash is SHA-256("1" + e164_number) as a hex string.
	// This is the only identifier stored — no plaintext numbers ever.
	NumberHash string `json:"hash" db:"number_hash"`

	// Source identifies where this number was found (e.g., "ftc", "bbb", "reddit", "user_report").
	Source string `json:"source,omitempty" db:"source"`

	// ScamType categorizes the scam (e.g., "irs-impersonation", "tech-support", "grandparent").
	ScamType string `json:"scam_type,omitempty" db:"scam_type"`

	// RiskScore is a float between 0.0 (safe) and 1.0 (confirmed scam).
	RiskScore float64 `json:"risk_score" db:"risk_score"`

	// ReportCount tracks how many times this number has been reported.
	ReportCount int `json:"report_count,omitempty" db:"report_count"`

	// FirstSeen is when this number first appeared in our database.
	FirstSeen time.Time `json:"first_seen,omitempty" db:"first_seen"`

	// LastUpdated is when this record was last modified.
	LastUpdated time.Time `json:"last_updated,omitempty" db:"last_updated"`

	// ExpiresAt is when this record should be re-evaluated (auto-expiry for aging entries).
	ExpiresAt *time.Time `json:"expires_at,omitempty" db:"expires_at"`
}

// CheckResponse is the JSON response for a number lookup.
type CheckResponse struct {
	Hash      string   `json:"hash"`
	RiskScore float64  `json:"risk_score"`
	Risk      float64  `json:"risk"`              // iOS compat
	ScamType  string   `json:"scam_type,omitempty"`
	Label     string   `json:"label,omitempty"`   // iOS compat
	Tags      []string `json:"tags,omitempty"`
	Source    string   `json:"source,omitempty"`
	Found     bool     `json:"found"`
	// iOS-compatible fields
	Confidence     float64 `json:"confidence"`
	ReportCount    int     `json:"report_count"`
	IsConfirmed    bool    `json:"is_confirmed"`
	FirstReported  int64   `json:"first_reported_at,omitempty"`
	SuggestedAction string `json:"suggested_action,omitempty"`
}
