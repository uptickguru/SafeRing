package carrier

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"

	"go.uber.org/zap"
)

// LookupResult contains carrier information for a phone number
type LookupResult struct {
	PhoneNumber     string `json:"phone_number"`
	CountryCode     string `json:"country_code"`
	NationalFormat  string `json:"national_format"`
	Carrier         string `json:"carrier"`
	CarrierType     string `json:"carrier_type"` // mobile, landline, voip
	CallerName      string `json:"caller_name,omitempty"`
	IsOurAccount    bool   `json:"is_our_account"`   // True if this is from our SignalWire/Twilio account
	AccountProvider string `json:"account_provider"` // "signalwire", "twilio", ""
}

// LookupService provides carrier identification for phone numbers
type LookupService struct {
	logger          *zap.Logger
	twilioSID       string
	twilioToken     string
	signalwireSID   string
	signalwireToken string
	signalwireSpace string
	httpClient      *http.Client
}

// NewLookupService creates a carrier lookup service from environment variables
func NewLookupService(logger *zap.Logger) *LookupService {
	return &LookupService{
		logger:          logger.Named("carrier.lookup"),
		twilioSID:       os.Getenv("TWILIO_ACCOUNT_SID"),
		twilioToken:     os.Getenv("TWILIO_AUTH_TOKEN"),
		signalwireSID:   os.Getenv("SIGNALWIRE_PROJECT_ID"),
		signalwireToken: os.Getenv("SIGNALWIRE_API_TOKEN"),
		signalwireSpace: os.Getenv("SIGNALWIRE_SPACE_URL"),
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

// Enabled returns true if at least one carrier API is configured
func (s *LookupService) Enabled() bool {
	return (s.twilioSID != "" && s.twilioToken != "") ||
		(s.signalwireSID != "" && s.signalwireToken != "" && s.signalwireSpace != "")
}

// Lookup identifies the carrier for a phone number
// Tries SignalWire first (if configured), then Twilio
func (s *LookupService) Lookup(ctx context.Context, phoneNumber string) (*LookupResult, error) {
	// Try SignalWire first
	if s.signalwireSID != "" && s.signalwireToken != "" && s.signalwireSpace != "" {
		result, err := s.lookupSignalWire(ctx, phoneNumber)
		if err == nil {
			return result, nil
		}
		s.logger.Warn("signalwire lookup failed, trying twilio",
			zap.String("number", phoneNumber),
			zap.Error(err))
	}

	// Fall back to Twilio
	if s.twilioSID != "" && s.twilioToken != "" {
		return s.lookupTwilio(ctx, phoneNumber)
	}

	return nil, fmt.Errorf("no carrier lookup service configured")
}

// lookupSignalWire uses SignalWire's Lookup API
// Endpoint: GET /api/laml/2010-04-01/PhoneNumbers/{PhoneNumber}.json?Type=carrier
func (s *LookupService) lookupSignalWire(ctx context.Context, phoneNumber string) (*LookupResult, error) {
	url := fmt.Sprintf("%s/api/laml/2010-04-01/PhoneNumbers/%s.json?Type=carrier",
		s.signalwireSpace, phoneNumber)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(s.signalwireSID, s.signalwireToken)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode >= 300 {
		return nil, fmt.Errorf("signalwire lookup %d: %s", resp.StatusCode, string(body))
	}

	var data struct {
		PhoneNumber    string `json:"phone_number"`
		CountryCode    string `json:"country_code"`
		NationalFormat string `json:"national_format"`
		Carrier        struct {
			Name string `json:"name"`
			Type string `json:"type"`
		} `json:"carrier"`
		CallerName struct {
			CallerName string `json:"caller_name"`
		} `json:"caller_name"`
	}

	if err := json.Unmarshal(body, &data); err != nil {
		return nil, fmt.Errorf("parse signalwire response: %w", err)
	}

	result := &LookupResult{
		PhoneNumber:    data.PhoneNumber,
		CountryCode:    data.CountryCode,
		NationalFormat: data.NationalFormat,
		Carrier:        data.Carrier.Name,
		CarrierType:    data.Carrier.Type,
		CallerName:     data.CallerName.CallerName,
	}

	// Check if this is from our own SignalWire account
	// We'd need to check against our known numbers list
	// For now, we'll mark it as potentially ours if carrier matches
	if data.Carrier.Name == "SignalWire" {
		result.AccountProvider = "signalwire"
		// TODO: Cross-reference with our allowlist to confirm
	}

	return result, nil
}

// lookupTwilio uses Twilio's Lookup API v2
// Endpoint: GET /v1/PhoneNumbers/{PhoneNumber}?Type=carrier
func (s *LookupService) lookupTwilio(ctx context.Context, phoneNumber string) (*LookupResult, error) {
	url := fmt.Sprintf("https://lookups.twilio.com/v2/PhoneNumbers/%s?Fields=carrier,caller_name",
		phoneNumber)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.SetBasicAuth(s.twilioSID, s.twilioToken)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode >= 300 {
		return nil, fmt.Errorf("twilio lookup %d: %s", resp.StatusCode, string(body))
	}

	var data struct {
		PhoneNumber    string `json:"phone_number"`
		CountryCode    string `json:"country_code"`
		NationalFormat string `json:"national_format"`
		Carrier        struct {
			Name string `json:"name"`
			Type string `json:"type"`
		} `json:"carrier"`
		CallerName struct {
			CallerName string `json:"caller_name"`
		} `json:"caller_name"`
	}

	if err := json.Unmarshal(body, &data); err != nil {
		return nil, fmt.Errorf("parse twilio response: %w", err)
	}

	result := &LookupResult{
		PhoneNumber:    data.PhoneNumber,
		CountryCode:    data.CountryCode,
		NationalFormat: data.NationalFormat,
		Carrier:        data.Carrier.Name,
		CarrierType:    data.Carrier.Type,
		CallerName:     data.CallerName.CallerName,
	}

	// Check if this is from our own Twilio account
	if data.Carrier.Name == "Twilio" {
		result.AccountProvider = "twilio"
		// TODO: Cross-reference with our allowlist to confirm
	}

	return result, nil
}

// IsFromKnownScamCarrier checks if a carrier is commonly used for scams
// Based on patterns we've observed
func (s *LookupService) IsFromKnownScamCarrier(carrierName string) bool {
	// These carriers are frequently abused for scam operations
	// This is a heuristic, not definitive
	suspiciousCarriers := []string{
		// Add carriers as we identify them from patterns
		// Example: "Bandwidth.com", "Peerless Network", etc.
	}

	for _, suspicious := range suspiciousCarriers {
		if carrierName == suspicious {
			return true
		}
	}
	return false
}
