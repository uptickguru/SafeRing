package handler

import (
	"encoding/json"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/safecall"
)

// SafeCallHandler: HITL incidents + SignalWire IVR + hangup + SSE.
type SafeCallHandler struct {
	logger *zap.Logger
	store  *safecall.Store
	hub    *safecall.Hub
	sw     *safecall.SWClient
	// gather attempts per CallSid
	mu       sync.Mutex
	attempts map[string]int
	publicBase string // e.g. https://safering.deathbyathousand.com
	codeTTL    time.Duration
}

func NewSafeCallHandler(logger *zap.Logger) *SafeCallHandler {
	base := strings.TrimRight(os.Getenv("SAFECALL_PUBLIC_BASE"), "/")
	if base == "" {
		base = strings.TrimRight(os.Getenv("PUBLIC_BASE_URL"), "/")
	}
	if base == "" {
		base = "https://safering.deathbyathousand.com"
	}
	ttl := 20 * time.Minute
	if v := os.Getenv("SAFECALL_CODE_TTL"); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			ttl = d
		}
	}
	return &SafeCallHandler{
		logger:     logger.Named("handler.safecall"),
		store:      safecall.NewStore(),
		hub:        safecall.NewHub(),
		sw:         safecall.NewSWClientFromEnv(),
		attempts:   map[string]int{},
		publicBase: base,
		codeTTL:    ttl,
	}
}

// --- REST ---

type createIncidentReq struct {
	HouseholdID string `json:"household_id"`
	SeniorE164  string `json:"senior_e164"`
	TrustedE164 string `json:"trusted_e164"`
	SuspectHint string `json:"suspect_hint"`
}

func (h *SafeCallHandler) CreateIncident(w http.ResponseWriter, r *http.Request) {
	var req createIncidentReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "bad json")
		return
	}
	req.HouseholdID = strings.TrimSpace(req.HouseholdID)
	if req.HouseholdID == "" || req.SeniorE164 == "" {
		writeError(w, http.StatusBadRequest, "household_id and senior_e164 required")
		return
	}
	inc := h.store.CreatePending(req.HouseholdID, req.SeniorE164, req.TrustedE164, req.SuspectHint)
	h.hub.Publish(req.HouseholdID, safecall.Event{
		Type: "incident_pending", IncidentID: inc.ID, Household: req.HouseholdID,
		Payload: map[string]interface{}{"incident": inc},
	})
	writeJSON(w, http.StatusCreated, inc)
}

func (h *SafeCallHandler) GetIncident(w http.ResponseWriter, r *http.Request) {
	id := urlParamChi(r, "id")
	inc, ok := h.store.Get(id)
	if !ok {
		writeError(w, http.StatusNotFound, "not found")
		return
	}
	writeJSON(w, http.StatusOK, inc)
}

func (h *SafeCallHandler) ListIncidents(w http.ResponseWriter, r *http.Request) {
	hh := r.URL.Query().Get("household_id")
	if hh == "" {
		writeError(w, http.StatusBadRequest, "household_id required")
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"incidents": h.store.ListHousehold(hh, 50),
	})
}

func (h *SafeCallHandler) Approve(w http.ResponseWriter, r *http.Request) {
	id := urlParamChi(r, "id")
	inc, code, err := h.store.Approve(id, h.codeTTL)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	// Include code in response for caregiver UI
	type resp struct {
		*safecall.Incident
		Code            string    `json:"code"`
		SafeCallNumber  string    `json:"safecall_number"`
		CodeExpiresAt   time.Time `json:"code_expires_at"`
		Instructions    string    `json:"instructions"`
	}
	did := os.Getenv("SAFECALL_DID")
	out := resp{
		Incident:       inc,
		Code:           code,
		SafeCallNumber: did,
		CodeExpiresAt:  inc.CodeExpiresAt,
		Instructions:   "Have them call SafeCall and enter code " + code + " then #.",
	}
	h.hub.Publish(inc.HouseholdID, safecall.Event{
		Type: "incident_approved", IncidentID: inc.ID, Household: inc.HouseholdID,
		Payload: map[string]interface{}{
			"code": code, "expires_at": inc.CodeExpiresAt, "safecall_did": did,
		},
	})
	writeJSON(w, http.StatusOK, out)
}

func (h *SafeCallHandler) Deny(w http.ResponseWriter, r *http.Request) {
	id := urlParamChi(r, "id")
	inc, err := h.store.Deny(id)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	h.hub.Publish(inc.HouseholdID, safecall.Event{
		Type: "incident_denied", IncidentID: inc.ID, Household: inc.HouseholdID,
	})
	writeJSON(w, http.StatusOK, inc)
}

func (h *SafeCallHandler) Hangup(w http.ResponseWriter, r *http.Request) {
	id := urlParamChi(r, "id")
	inc, ok := h.store.Get(id)
	if !ok {
		writeError(w, http.StatusNotFound, "not found")
		return
	}
	if inc.SWCallSID == "" {
		// still mark ended
		_, _ = h.store.MarkEnded(id)
		h.hub.Publish(inc.HouseholdID, safecall.Event{Type: "call_ended", IncidentID: id, Household: inc.HouseholdID})
		writeJSON(w, http.StatusOK, map[string]string{"status": "ended_no_sid"})
		return
	}
	if err := h.sw.HangupCall(inc.SWCallSID); err != nil {
		h.logger.Warn("hangup failed", zap.Error(err), zap.String("sid", inc.SWCallSID))
		// still mark local
		_, _ = h.store.MarkEnded(id)
		writeError(w, http.StatusBadGateway, "hangup: "+err.Error())
		return
	}
	_, _ = h.store.MarkEnded(id)
	h.hub.Publish(inc.HouseholdID, safecall.Event{Type: "call_ended", IncidentID: id, Household: inc.HouseholdID})
	writeJSON(w, http.StatusOK, map[string]string{"status": "hangup_sent"})
}

func (h *SafeCallHandler) EventsSSE(w http.ResponseWriter, r *http.Request) {
	hh := r.URL.Query().Get("household_id")
	if hh == "" {
		writeError(w, http.StatusBadRequest, "household_id required")
		return
	}
	h.hub.ServeSSE(w, r, hh)
}

func (h *SafeCallHandler) Status(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"safecall":          true,
		"signalwire":        h.sw.Enabled(),
		"public_base":       h.publicBase,
		"did_configured":    os.Getenv("SAFECALL_DID") != "",
		"code_ttl":          h.codeTTL.String(),
		"realtime":          "sse",
		"realtime_note":     "SSE at GET /v1/safecall/events?household_id= — SignalR-class fanout without .NET",
	})
}

// --- SignalWire webhooks (form POST) ---

func (h *SafeCallHandler) SWInbound(w http.ResponseWriter, r *http.Request) {
	_ = r.ParseForm()
	callSID := r.FormValue("CallSid")
	from := r.FormValue("From")
	h.logger.Info("sw inbound", zap.String("sid", callSID), zap.String("from", from))
	action := h.publicBase + "/v1/safecall/sw/gather"
	writeLaML(w, safecall.BuildInboundIVR(action))
}

func (h *SafeCallHandler) SWGather(w http.ResponseWriter, r *http.Request) {
	_ = r.ParseForm()
	callSID := r.FormValue("CallSid")
	from := r.FormValue("From")
	digits := r.FormValue("Digits")
	h.mu.Lock()
	h.attempts[callSID]++
	n := h.attempts[callSID]
	h.mu.Unlock()

	inc, err := h.store.ValidateCode(digits)
	if err != nil {
		h.logger.Info("code fail", zap.String("digits", digits), zap.Error(err), zap.Int("attempt", n))
		retry := h.publicBase + "/v1/safecall/sw/gather"
		if n >= 3 {
			writeLaML(w, safecall.BuildInvalidCode(retry, 0))
			return
		}
		writeLaML(w, safecall.BuildInvalidCode(retry, 3-n))
		return
	}

	conf := "sc_" + inc.ID
	_, _ = h.store.MarkBridging(inc.ID, from, callSID, conf)
	h.hub.Publish(inc.HouseholdID, safecall.Event{
		Type: "bridge_starting", IncidentID: inc.ID, Household: inc.HouseholdID,
		Payload: map[string]interface{}{"from": from, "call_sid": callSID},
	})

	statusCB := h.publicBase + "/v1/safecall/sw/status?incident_id=" + url.QueryEscape(inc.ID)
	writeLaML(w, safecall.BuildBridge(inc.SeniorE164, inc.TrustedE164, statusCB))
}

func (h *SafeCallHandler) SWStatus(w http.ResponseWriter, r *http.Request) {
	_ = r.ParseForm()
	incID := r.URL.Query().Get("incident_id")
	callStatus := r.FormValue("DialCallStatus")
	if callStatus == "" {
		callStatus = r.FormValue("CallStatus")
	}
	callSID := r.FormValue("CallSid")
	h.logger.Info("sw status", zap.String("incident", incID), zap.String("status", callStatus), zap.String("sid", callSID))
	if incID != "" {
		h.store.AttachCall(incID, callSID)
		switch strings.ToLower(callStatus) {
		case "in-progress", "answered", "bridged":
			if inc, err := h.store.MarkLive(incID); err == nil {
				h.hub.Publish(inc.HouseholdID, safecall.Event{Type: "call_live", IncidentID: incID, Household: inc.HouseholdID})
			}
		case "completed", "busy", "no-answer", "failed", "canceled":
			if inc, err := h.store.MarkEnded(incID); err == nil {
				h.hub.Publish(inc.HouseholdID, safecall.Event{Type: "call_ended", IncidentID: incID, Household: inc.HouseholdID,
					Payload: map[string]interface{}{"status": callStatus}})
			}
		}
	}
	// empty 200
	w.WriteHeader(http.StatusOK)
	_, _ = io.WriteString(w, "ok")
}

func writeLaML(w http.ResponseWriter, xml string) {
	w.Header().Set("Content-Type", "text/xml; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(xml))
}

