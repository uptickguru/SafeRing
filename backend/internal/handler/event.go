package handler

import (
	"encoding/json"
	"net/http"

	"go.uber.org/zap"
)

// EventHandler handles POST /v1/event
// Accepts lightweight action reports from device apps.
//
// These are fire-and-forget telemetry events that let us see what
// each device is doing in real time: blocking calls, warning users,
// monitoring SMS, etc.
//
// No database storage — events are purely operational logging.
// The middleware already captures every HTTP request with metadata.
// This endpoint validates the payload structure, logs it, and returns OK.
type EventHandler struct {
	logger *zap.Logger
}

// NewEventHandler creates a new EventHandler.
func NewEventHandler(logger *zap.Logger) *EventHandler {
	return &EventHandler{
		logger: logger.Named("handler.event"),
	}
}

// DeviceEvent is the JSON body for POST /v1/event.
type DeviceEvent struct {
	Platform   string  `json:"platform"`              // "android" | "ios"
	Action     string  `json:"action"`                // "block" | "warn" | "monitor" | "check"
	EventType  string  `json:"event_type"`            // "call" | "sms"
	HashPrefix string  `json:"hash_prefix,omitempty"` // first 8 chars of SHA-256 hash
	RiskScore  float64 `json:"risk_score,omitempty"`  // 0.0 - 1.0
	ScamType   string  `json:"scam_type,omitempty"`   // optional classification label
	Source     string  `json:"source,omitempty"`      // "local_cache" | "api" | "ml"
}

func (e *DeviceEvent) validate() bool {
	if e.Platform != "android" && e.Platform != "ios" {
		return false
	}
	if e.Action != "block" && e.Action != "warn" && e.Action != "monitor" && e.Action != "check" {
		return false
	}
	if e.EventType != "call" && e.EventType != "sms" {
		return false
	}
	return true
}

// ServeHTTP handles POST /v1/event.
func (h *EventHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var event DeviceEvent
	if err := json.NewDecoder(r.Body).Decode(&event); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	if !event.validate() {
		writeError(w, http.StatusBadRequest, "invalid event: platform, action, and event_type are required")
		return
	}

	// Structured log — shows up in server logs alongside middleware request logs
	h.logger.Info("device event",
		zap.String("platform", event.Platform),
		zap.String("action", event.Action),
		zap.String("event_type", event.EventType),
		zap.String("hash_prefix", event.HashPrefix),
		zap.Float64("risk_score", event.RiskScore),
		zap.String("scam_type", event.ScamType),
		zap.String("source", event.Source),
	)

	writeJSON(w, http.StatusOK, map[string]string{
		"status": "accepted",
	})
}
