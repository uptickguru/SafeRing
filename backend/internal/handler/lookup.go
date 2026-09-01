package handler

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/phoneintel"
	"github.com/safering/backend/internal/store"
)

// LookupHandler handles POST /v1/lookup
// It combines scam number database lookup with phone number intelligence.
// The client sends a hash (for scam DB lookup) and a prefix (for intelligence).
type LookupHandler struct {
	numberStore    *store.ScamNumberStore
	allowlistStore *store.AllowlistStore
	intel          *phoneintel.Intelligence
	logger         *zap.Logger
}

// LookupRequest is the expected JSON body for POST /v1/lookup.
type LookupRequest struct {
	Hash   string `json:"hash"`            // SHA-256 hex hash of the full E.164 number
	Prefix string `json:"prefix"`          // First 5+ digits of E.164 (enough for country/carrier, not enough to identify)
	Token  string `json:"token,omitempty"` // Auth token (validated by middleware if configured)
}

// LookupResponse is the JSON response for POST /v1/lookup.
type LookupResponse struct {
	Hash        string                    `json:"hash"`
	RiskScore   float64                   `json:"risk_score"`
	Label       string                    `json:"label,omitempty"`
	Origin      *phoneintel.NumberIntelligence `json:"origin,omitempty"`
	Source      string                    `json:"source,omitempty"`
	ReportCount int                       `json:"report_count,omitempty"`
	FirstSeen   int64                     `json:"first_seen,omitempty"`
	STIRStatus  string                    `json:"stir_status"`
	Found       bool                      `json:"found"`
	Allowlisted bool                      `json:"allowlisted"`
}

// NewLookupHandler creates a new LookupHandler.
func NewLookupHandler(numberStore *store.ScamNumberStore, allowlistStore *store.AllowlistStore, intel *phoneintel.Intelligence, logger *zap.Logger) *LookupHandler {
	return &LookupHandler{
		numberStore:    numberStore,
		allowlistStore: allowlistStore,
		intel:          intel,
		logger:         logger.Named("handler.lookup"),
	}
}

// LookupRateLimit is the rate limit for the lookup endpoint (200/min per IP).
// Higher than /check because this fires on every incoming call.
const LookupRateLimit = 200

// ServeHTTP handles POST /v1/lookup requests.
func (h *LookupHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Parse request body
	var req LookupRequest
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}
	defer r.Body.Close()

	// Validate hash
	if req.Hash == "" {
		writeError(w, http.StatusBadRequest, "missing required field: hash")
		return
	}
	if len(req.Hash) != 64 || !isHexString(req.Hash) {
		writeError(w, http.StatusBadRequest, "invalid hash: must be a 64-character hex string (SHA-256)")
		return
	}

	// Validate prefix (must be digits only, 1-15 chars)
	if req.Prefix == "" {
		writeError(w, http.StatusBadRequest, "missing required field: prefix")
		return
	}
	req.Prefix = strings.TrimPrefix(req.Prefix, "+")
	if len(req.Prefix) < 1 || len(req.Prefix) > 15 {
		writeError(w, http.StatusBadRequest, "invalid prefix: must be 1-15 digits")
		return
	}
	for _, ch := range req.Prefix {
		if ch < '0' || ch > '9' {
			writeError(w, http.StatusBadRequest, "invalid prefix: must contain only digits")
			return
		}
	}

	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	// Build response
	resp := LookupResponse{
		Hash:       req.Hash,
		STIRStatus: "unknown",
	}

	// 1. Check allowlist first
	allowed, err := h.allowlistStore.IsAllowed(ctx, req.Hash)
	if err != nil {
		h.logger.Warn("allowlist check error", zap.Error(err))
	}
	if allowed {
		entry, _ := h.allowlistStore.GetByHash(ctx, req.Hash)
		label := "Known Good"
		if entry != nil && entry.Label != "" {
			label = entry.Label
		}
		resp.Found = true
		resp.Allowlisted = true
		resp.RiskScore = 0.0
		resp.Label = label
		resp.Source = "allowlist"
		resp.ReportCount = 0

		// Still do phone intelligence lookup for context
		intelResult := h.intel.Lookup(req.Prefix)
		if intelResult.IsValid {
			resp.Origin = &intelResult
		}

		writeJSON(w, http.StatusOK, resp)
		h.logger.Debug("lookup: allowlisted",
			zap.String("hash_prefix", req.Hash[:8]),
			zap.String("label", label),
		)
		return
	}

	// 2. Look up the hash in the scam number store
	sn, err := h.numberStore.GetByHash(ctx, req.Hash)
	if err != nil && err.Error() != "record not found" {
		h.logger.Error("database lookup failed", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	if sn != nil {
		resp.Found = true
		resp.RiskScore = sn.RiskScore
		resp.Label = sn.ScamType
		resp.Source = sn.Source
		resp.ReportCount = sn.ReportCount
		if !sn.FirstSeen.IsZero() {
			resp.FirstSeen = sn.FirstSeen.Unix()
		}
	}

	// 3. Perform phone intelligence lookup on the prefix
	intelResult := h.intel.Lookup(req.Prefix)
	if intelResult.IsValid {
		resp.Origin = &intelResult
	}

	writeJSON(w, http.StatusOK, resp)

	if resp.Found {
		h.logger.Info("lookup: scam found",
			zap.String("hash_prefix", req.Hash[:8]),
			zap.Float64("risk", resp.RiskScore),
			zap.String("label", resp.Label),
			zap.String("origin_country", intelResult.Country),
			zap.String("origin_carrier", intelResult.Carrier),
		)
	} else {
		h.logger.Debug("lookup: no match",
			zap.String("hash_prefix", req.Hash[:8]),
			zap.String("origin_country", intelResult.Country),
		)
	}
}
