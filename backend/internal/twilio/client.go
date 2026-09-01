package twilio

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"time"
)

// Client is a Twilio REST API client
type Client struct {
	AccountSID string
	AuthToken  string
	HTTP       *http.Client
}

// NewClientFromEnv creates a Twilio client from environment variables
func NewClientFromEnv() *Client {
	return &Client{
		AccountSID: os.Getenv("TWILIO_ACCOUNT_SID"),
		AuthToken:  os.Getenv("TWILIO_AUTH_TOKEN"),
		HTTP:       &http.Client{Timeout: 30 * time.Second},
	}
}

// Enabled returns true if Twilio credentials are configured
func (c *Client) Enabled() bool {
	return c != nil && c.AccountSID != "" && c.AuthToken != ""
}

// IncomingPhoneNumber represents a Twilio phone number
type IncomingPhoneNumber struct {
	SID          string `json:"sid"`
	PhoneNumber  string `json:"phone_number"`
	FriendlyName string `json:"friendly_name"`
	Status       string `json:"status"`
	DateCreated  string `json:"date_created"`
	DateUpdated  string `json:"date_updated"`
	Capabilities struct {
		Voice bool `json:"voice"`
		SMS   bool `json:"sms"`
		MMS   bool `json:"mms"`
	} `json:"capabilities"`
	VoiceURL string `json:"voice_url"`
	SMSURL   string `json:"sms_url"`
}

// PhoneNumbersResponse is the Twilio API response for listing numbers
type PhoneNumbersResponse struct {
	PhoneNumberList      []IncomingPhoneNumber `json:"incoming_phone_numbers"`
	Page                 int                   `json:"page"`
	PageSize             int                   `json:"page_size"`
	FirstPageURI         string                `json:"first_page_uri"`
	NextPageURI          string                `json:"next_page_uri"`
	PreviousPageURI      string                `json:"previous_page_uri"`
	URI                  string                `json:"uri"`
}

// ListPhoneNumbers retrieves all phone numbers from the Twilio account
// Paginates through all pages and returns the complete list
func (c *Client) ListPhoneNumbers(ctx context.Context) ([]IncomingPhoneNumber, error) {
	if !c.Enabled() {
		return nil, fmt.Errorf("twilio not configured")
	}

	var allNumbers []IncomingPhoneNumber
	pageURL := fmt.Sprintf("https://api.twilio.com/2010-04-01/Accounts/%s/IncomingPhoneNumbers.json?PageSize=50",
		c.AccountSID)

	for pageURL != "" {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, pageURL, nil)
		if err != nil {
			return nil, err
		}
		req.SetBasicAuth(c.AccountSID, c.AuthToken)

		resp, err := c.HTTP.Do(req)
		if err != nil {
			return nil, err
		}

		body, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			return nil, err
		}

		if resp.StatusCode >= 300 {
			return nil, fmt.Errorf("twilio list numbers %d: %s", resp.StatusCode, string(body))
		}

		var response PhoneNumbersResponse
		if err := json.Unmarshal(body, &response); err != nil {
			return nil, fmt.Errorf("twilio parse response: %w", err)
		}

		allNumbers = append(allNumbers, response.PhoneNumberList...)

		// Check for next page
		if response.NextPageURI != "" {
			if response.NextPageURI[0] == '/' {
				pageURL = "https://api.twilio.com" + response.NextPageURI
			} else {
				pageURL = response.NextPageURI
			}
		} else {
			pageURL = ""
		}
	}

	return allNumbers, nil
}

// LookupNumber performs a carrier lookup on a phone number
// Returns carrier name and type
func (c *Client) LookupNumber(ctx context.Context, phoneNumber string) (carrierName, carrierType string, err error) {
	if !c.Enabled() {
		return "", "", fmt.Errorf("twilio not configured")
	}

	// URL encode the phone number
	encodedNumber := url.QueryEscape(phoneNumber)
	lookupURL := fmt.Sprintf("https://lookups.twilio.com/v2/PhoneNumbers/%s?Fields=carrier",
		encodedNumber)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, lookupURL, nil)
	if err != nil {
		return "", "", err
	}
	req.SetBasicAuth(c.AccountSID, c.AuthToken)

	resp, err := c.HTTP.Do(req)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", "", err
	}

	if resp.StatusCode >= 300 {
		return "", "", fmt.Errorf("twilio lookup %d: %s", resp.StatusCode, string(body))
	}

	var result struct {
		Carrier struct {
			Name string `json:"name"`
			Type string `json:"type"`
		} `json:"carrier"`
	}

	if err := json.Unmarshal(body, &result); err != nil {
		return "", "", fmt.Errorf("twilio parse lookup: %w", err)
	}

	return result.Carrier.Name, result.Carrier.Type, nil
}
