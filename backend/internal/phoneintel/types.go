// Package phoneintel provides phone number intelligence: country, carrier,
// line type, and region detection from phone number prefixes.
//
// The service uses an in-memory prefix trie for O(log n) lookups and is
// completely offline-capable after initialization. The trie is immutable
// after Load(), making it safe for concurrent reads without locks.
package phoneintel

// NumberIntelligence contains all lookup results for a phone number.
type NumberIntelligence struct {
	Country     string `json:"country"`                // ISO 3166-1 alpha-2 (e.g., "US", "NG", "GB")
	CountryName string `json:"country_name"`           // Human-readable country name (e.g., "United States")
	Region      string `json:"region,omitempty"`       // State/province (e.g., "California", "Lagos")
	City        string `json:"city,omitempty"`         // City where available (e.g., "Los Angeles")
	Carrier     string `json:"carrier,omitempty"`      // Carrier name (e.g., "MTN Nigeria", "AT&T")
	LineType    string `json:"line_type"`              // mobile, landline, voip, toll_free, premium, unknown
	IsValid     bool   `json:"is_valid"`               // Whether the prefix was recognized
	E164        string `json:"e164,omitempty"`         // Normalized E.164 form if available
}

// PrefixEntry represents a single prefix record in the database.
type PrefixEntry struct {
	Prefix      string // Numeric prefix (e.g., "1212", "23480")
	Country     string // ISO 3166-1 alpha-2
	CountryName string // Human-readable country name
	Region      string // State/province (optional)
	City        string // City (optional)
	Carrier     string // Carrier name (optional)
	LineType    string // mobile, landline, voip, toll_free, premium, unknown
}
