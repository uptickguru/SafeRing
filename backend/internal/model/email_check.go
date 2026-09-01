package model

// EmailCheckRequest represents a request to check email content for phishing.
type EmailCheckRequest struct {
	Sender           string   `json:"sender"`
	Subject          string   `json:"subject"`
	Body             string   `json:"body"`
	AttachmentHashes []string `json:"attachment_hashes,omitempty"`
	Source           string   `json:"source"` // "email", "paste", "forward"
}

// EmailCheckResponse represents the result of an email phishing check.
type EmailCheckResponse struct {
	RiskScore         float64              `json:"risk_score"`
	IsPhishing        bool                 `json:"is_phishing"`
	PhishingType      string               `json:"phishing_type,omitempty"`
	Indicators        []PhishingIndicator  `json:"indicators,omitempty"`
	MaliciousURLs     []string             `json:"malicious_urls,omitempty"`
	SenderReputation  string               `json:"sender_reputation"` // "unknown", "trusted", "suspicious", "malicious"
	AttachmentRisk    string               `json:"attachment_risk"`   // "none", "low", "medium", "high"
	Recommendation    string               `json:"recommendation"`
}

// PhishingIndicator describes a single phishing signal found in the email.
type PhishingIndicator struct {
	Type        string  `json:"type"`        // "url", "sender", "keyword", "attachment", "header"
	Severity    string  `json:"severity"`    // "low", "medium", "high", "critical"
	Description string  `json:"description"`
	Confidence  float64 `json:"confidence"`
}
