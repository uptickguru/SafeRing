package safecall

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// SignalWire REST helper (Compatibility API).
type SWClient struct {
	ProjectID string
	Token     string
	SpaceURL  string // e.g. https://example.signalwire.com
	HTTP      *http.Client
}

func NewSWClientFromEnv() *SWClient {
	return &SWClient{
		ProjectID: os.Getenv("SIGNALWIRE_PROJECT_ID"),
		Token:     os.Getenv("SIGNALWIRE_API_TOKEN"),
		SpaceURL:  strings.TrimRight(os.Getenv("SIGNALWIRE_SPACE_URL"), "/"),
		HTTP:      &http.Client{Timeout: 15 * time.Second},
	}
}

func (c *SWClient) Enabled() bool {
	return c != nil && c.ProjectID != "" && c.Token != "" && c.SpaceURL != ""
}

// HangupCall completes a call by SID.
func (c *SWClient) HangupCall(callSID string) error {
	if !c.Enabled() {
		return fmt.Errorf("signalwire not configured")
	}
	if callSID == "" {
		return fmt.Errorf("empty call sid")
	}
	u := fmt.Sprintf("%s/api/laml/2010-04-01/Accounts/%s/Calls/%s.json", c.SpaceURL, c.ProjectID, callSID)
	form := url.Values{}
	form.Set("Status", "completed")
	req, err := http.NewRequest(http.MethodPost, u, strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	req.SetBasicAuth(c.ProjectID, c.Token)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := c.HTTP.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 300 {
		return fmt.Errorf("sw hangup %d: %s", resp.StatusCode, string(body))
	}
	return nil
}

// PhoneNumbersResponse represents the SignalWire API response for listing phone numbers.
type PhoneNumbersResponse struct {
	IncomingPhoneNumbers []IncomingPhoneNumber `json:"incoming_phone_numbers"`
	Page                 int                   `json:"page"`
	PageSize             int                   `json:"page_size"`
	URI                  string                `json:"uri"`
	FirstPageURI         string                `json:"first_page_uri"`
	NextPageURI          string                `json:"next_page_uri"`
	PreviousPageURI      string                `json:"previous_page_uri"`
}

// IncomingPhoneNumber represents a single phone number from SignalWire.
type IncomingPhoneNumber struct {
	SID            string `json:"sid"`
	AccountSID     string `json:"account_sid"`
	PhoneNumber    string `json:"phone_number"`
	FriendlyName   string `json:"friendly_name"`
	DateCreated    string `json:"date_created"`
	DateUpdated    string `json:"date_updated"`
	Capabilities   struct {
		Voice bool `json:"voice"`
		SMS   bool `json:"sms"`
		MMS   bool `json:"mms"`
	} `json:"capabilities"`
	Status         string `json:"status"`
}

// ListPhoneNumbers retrieves all phone numbers owned by this SignalWire account.
// Paginates through all pages and returns the complete list.
func (c *SWClient) ListPhoneNumbers(ctx context.Context) ([]IncomingPhoneNumber, error) {
	if !c.Enabled() {
		return nil, fmt.Errorf("signalwire not configured")
	}

	var allNumbers []IncomingPhoneNumber
	pageURL := fmt.Sprintf("%s/api/laml/2010-04-01/Accounts/%s/IncomingPhoneNumbers.json?PageSize=50",
		c.SpaceURL, c.ProjectID)

	for pageURL != "" {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, pageURL, nil)
		if err != nil {
			return nil, err
		}
		req.SetBasicAuth(c.ProjectID, c.Token)

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
			return nil, fmt.Errorf("sw list numbers %d: %s", resp.StatusCode, string(body))
		}

		var response PhoneNumbersResponse
		if err := json.Unmarshal(body, &response); err != nil {
			return nil, fmt.Errorf("sw parse response: %w", err)
		}

		allNumbers = append(allNumbers, response.IncomingPhoneNumbers...)

		// Check for next page
		if response.NextPageURI != "" {
			if strings.HasPrefix(response.NextPageURI, "http") {
				pageURL = response.NextPageURI
			} else {
				pageURL = c.SpaceURL + response.NextPageURI
			}
		} else {
			pageURL = ""
		}
	}

	return allNumbers, nil
}

// xmlEscape minimal
func xmlEscape(s string) string {
	r := strings.NewReplacer(
		"&", "&amp;",
		"<", "&lt;",
		">", "&gt;",
		"\"", "&quot;",
		"'", "&apos;",
	)
	return r.Replace(s)
}

// BuildInboundIVR LaML: announce + gather code.
func BuildInboundIVR(actionURL string) string {
	// Bank-style consent + code
	say := "You have reached the G M G Safe Call Monitor system. " +
		"This call may be monitored and recorded for safety and training. " +
		"Enter your access code, then press pound. " +
		"By entering the code, you agree that this call is monitored and may be recorded."
	return fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Gather input="dtmf" timeout="12" finishOnKey="#" numDigits="6" action="%s" method="POST">
    <Say voice="alice">%s</Say>
  </Gather>
  <Say voice="alice">We did not receive a code. Goodbye.</Say>
  <Hangup/>
</Response>`, xmlEscape(actionURL), xmlEscape(say))
}

func BuildInvalidCode(retryURL string, remaining int) string {
	if remaining <= 0 {
		return `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Say voice="alice">Invalid code. Goodbye.</Say>
  <Hangup/>
</Response>`
	}
	return fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Gather input="dtmf" timeout="12" finishOnKey="#" numDigits="6" action="%s" method="POST">
    <Say voice="alice">That code was not valid. Enter your access code, then press pound.</Say>
  </Gather>
  <Say voice="alice">Goodbye.</Say>
  <Hangup/>
</Response>`, xmlEscape(retryURL))
}

func BuildBridge(seniorE164, trustedE164, statusCallback string) string {
	// Dial both into a conference-like simultaneous ring via nested Dial
	// Compatibility: Dial multiple numbers
	confName := "safecall"
	_ = confName
	var b bytes.Buffer
	b.WriteString(`<?xml version="1.0" encoding="UTF-8"?>`)
	b.WriteString(`<Response>`)
	b.WriteString(`<Say voice="alice">Connecting. This call remains monitored.</Say>`)
	b.WriteString(`<Dial answerOnBridge="true" timeout="45"`)
	if statusCallback != "" {
		b.WriteString(` action="` + xmlEscape(statusCallback) + `"`)
	}
	b.WriteString(`>`)
	if seniorE164 != "" {
		b.WriteString(`<Number>` + xmlEscape(seniorE164) + `</Number>`)
	}
	if trustedE164 != "" {
		b.WriteString(`<Number>` + xmlEscape(trustedE164) + `</Number>`)
	}
	b.WriteString(`</Dial>`)
	b.WriteString(`<Say voice="alice">The parties could not be reached. Goodbye.</Say>`)
	b.WriteString(`<Hangup/></Response>`)
	return b.String()
}

// DebugJSON helper
func MustJSON(v interface{}) string {
	b, _ := json.Marshal(v)
	return string(b)
}
