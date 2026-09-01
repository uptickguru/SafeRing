// Package safecall implements GMG SafeCall: HITL codes, SignalWire IVR, bridge control, SSE fanout.
package safecall

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"sync"
	"time"
)

type IncidentStatus string

const (
	StatusPending  IncidentStatus = "pending"
	StatusApproved IncidentStatus = "approved"
	StatusDenied   IncidentStatus = "denied"
	StatusBridging IncidentStatus = "bridging"
	StatusLive     IncidentStatus = "live"
	StatusEnded    IncidentStatus = "ended"
	StatusExpired  IncidentStatus = "expired"
)

type Incident struct {
	ID              string         `json:"id"`
	HouseholdID     string         `json:"household_id"`
	SeniorE164      string         `json:"senior_e164"`
	TrustedE164     string         `json:"trusted_e164"`
	SuspectHint     string         `json:"suspect_hint,omitempty"` // optional free text; not required ANI
	Status          IncidentStatus `json:"status"`
	Code            string         `json:"code,omitempty"` // only returned once on approve
	CodeHash        string         `json:"-"`
	CodeExpiresAt   time.Time      `json:"code_expires_at,omitempty"`
	CodeConsumed    bool           `json:"code_consumed"`
	SWCallSID       string         `json:"sw_call_sid,omitempty"`
	SWConference    string         `json:"sw_conference,omitempty"`
	CallerFrom      string         `json:"caller_from,omitempty"` // ANI on SafeCall DID
	CreatedAt       time.Time      `json:"created_at"`
	UpdatedAt       time.Time      `json:"updated_at"`
}

type Event struct {
	Type       string                 `json:"type"`
	IncidentID string                 `json:"incident_id,omitempty"`
	Household  string                 `json:"household_id,omitempty"`
	At         time.Time              `json:"at"`
	Payload    map[string]interface{} `json:"payload,omitempty"`
}

// Store is process-local MVP storage (swap for SQLite/Postgres later).
type Store struct {
	mu        sync.RWMutex
	incidents map[string]*Incident
	// code -> incident id (active only)
	codes map[string]string
}

func NewStore() *Store {
	return &Store{
		incidents: map[string]*Incident{},
		codes:     map[string]string{},
	}
}

func (s *Store) CreatePending(householdID, senior, trusted, suspectHint string) *Incident {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now().UTC()
	inc := &Incident{
		ID:          newID("inc"),
		HouseholdID: householdID,
		SeniorE164:  senior,
		TrustedE164: trusted,
		SuspectHint: suspectHint,
		Status:      StatusPending,
		CreatedAt:   now,
		UpdatedAt:   now,
	}
	s.incidents[inc.ID] = inc
	return clone(inc)
}

func (s *Store) Get(id string) (*Incident, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	inc, ok := s.incidents[id]
	if !ok {
		return nil, false
	}
	return clone(inc), true
}

func (s *Store) ListHousehold(householdID string, limit int) []*Incident {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if limit <= 0 {
		limit = 50
	}
	out := make([]*Incident, 0, limit)
	for _, inc := range s.incidents {
		if inc.HouseholdID == householdID {
			out = append(out, clone(inc))
		}
	}
	// naive newest-first
	for i := 0; i < len(out); i++ {
		for j := i + 1; j < len(out); j++ {
			if out[j].CreatedAt.After(out[i].CreatedAt) {
				out[i], out[j] = out[j], out[i]
			}
		}
	}
	if len(out) > limit {
		out = out[:limit]
	}
	return out
}

// Approve generates a single-use code (TTL).
func (s *Store) Approve(id string, ttl time.Duration) (*Incident, string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	inc, ok := s.incidents[id]
	if !ok {
		return nil, "", fmt.Errorf("not found")
	}
	if inc.Status != StatusPending && inc.Status != StatusApproved {
		return nil, "", fmt.Errorf("cannot approve status=%s", inc.Status)
	}
	// invalidate prior code mapping
	for c, iid := range s.codes {
		if iid == id {
			delete(s.codes, c)
		}
	}
	code := randomCode(6)
	inc.Code = code
	inc.CodeHash = code // MVP plain; hash at rest later
	inc.CodeExpiresAt = time.Now().UTC().Add(ttl)
	inc.CodeConsumed = false
	inc.Status = StatusApproved
	inc.UpdatedAt = time.Now().UTC()
	s.codes[code] = id
	out := clone(inc)
	out.Code = code
	return out, code, nil
}

func (s *Store) Deny(id string) (*Incident, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	inc, ok := s.incidents[id]
	if !ok {
		return nil, fmt.Errorf("not found")
	}
	inc.Status = StatusDenied
	inc.UpdatedAt = time.Now().UTC()
	return clone(inc), nil
}

// ValidateCode returns incident if code ok (does not consume until bridge starts).
func (s *Store) ValidateCode(code string) (*Incident, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	code = digitsOnly(code)
	id, ok := s.codes[code]
	if !ok {
		return nil, fmt.Errorf("invalid code")
	}
	inc := s.incidents[id]
	if inc == nil {
		return nil, fmt.Errorf("invalid code")
	}
	if inc.CodeConsumed {
		return nil, fmt.Errorf("code already used")
	}
	if time.Now().UTC().After(inc.CodeExpiresAt) {
		inc.Status = StatusExpired
		delete(s.codes, code)
		return nil, fmt.Errorf("code expired")
	}
	if inc.Status != StatusApproved && inc.Status != StatusBridging {
		return nil, fmt.Errorf("incident not approved")
	}
	return clone(inc), nil
}

func (s *Store) MarkBridging(id, from, callSID, conf string) (*Incident, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	inc, ok := s.incidents[id]
	if !ok {
		return nil, fmt.Errorf("not found")
	}
	inc.Status = StatusBridging
	inc.CallerFrom = from
	inc.SWCallSID = callSID
	inc.SWConference = conf
	inc.CodeConsumed = true
	// remove code from active map
	for c, iid := range s.codes {
		if iid == id {
			delete(s.codes, c)
		}
	}
	inc.UpdatedAt = time.Now().UTC()
	return clone(inc), nil
}

func (s *Store) MarkLive(id string) (*Incident, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	inc, ok := s.incidents[id]
	if !ok {
		return nil, fmt.Errorf("not found")
	}
	inc.Status = StatusLive
	inc.UpdatedAt = time.Now().UTC()
	return clone(inc), nil
}

func (s *Store) MarkEnded(id string) (*Incident, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	inc, ok := s.incidents[id]
	if !ok {
		return nil, fmt.Errorf("not found")
	}
	inc.Status = StatusEnded
	inc.UpdatedAt = time.Now().UTC()
	return clone(inc), nil
}

func (s *Store) AttachCall(id, callSID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if inc, ok := s.incidents[id]; ok {
		inc.SWCallSID = callSID
		inc.UpdatedAt = time.Now().UTC()
	}
}

func clone(i *Incident) *Incident {
	c := *i
	// never leak code on later gets unless just approved
	if c.Status != StatusApproved || c.CodeConsumed {
		c.Code = ""
	}
	return &c
}

func newID(prefix string) string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return prefix + "_" + hex.EncodeToString(b)
}

func randomCode(n int) string {
	const digits = "0123456789"
	b := make([]byte, n)
	_, _ = rand.Read(b)
	out := make([]byte, n)
	for i := range b {
		out[i] = digits[int(b[i])%10]
	}
	return string(out)
}

func digitsOnly(s string) string {
	out := make([]byte, 0, len(s))
	for i := 0; i < len(s); i++ {
		if s[i] >= '0' && s[i] <= '9' {
			out = append(out, s[i])
		}
	}
	return string(out)
}
