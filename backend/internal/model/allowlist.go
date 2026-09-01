package model

import "time"

// AllowlistEntry represents a known-good phone number that should never be flagged as scam.
// These are numbers we own, trust, or have verified as legitimate (e.g., our own SignalWire numbers,
// bank hotlines, government services, family contacts).
type AllowlistEntry struct {
	ID         int64     `json:"id"`
	NumberHash string    `json:"number_hash"`
	TenantID   string    `json:"tenant_id,omitempty"`
	E164       string    `json:"e164,omitempty"`       // Original E.164 format (optional, for admin UI)
	Label      string    `json:"label"`                 // Human-readable label: "Chase Bank", "SignalWire IVR", "Mom"
	Source     string    `json:"source"`                // "signalwire", "manual", "bank", "government", "family"
	AddedAt    time.Time `json:"added_at"`
}

// AllowlistSource constants for tracking where the entry came from.
const (
	AllowlistSourceSignalWire  = "signalwire"
	AllowlistSourceManual      = "manual"
	AllowlistSourceBank        = "bank"
	AllowlistSourceGovernment  = "government"
	AllowlistSourceFamily      = "family"
	AllowlistSourceCarrier     = "carrier"
)
