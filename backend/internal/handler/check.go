package handler

import (
	"net/http"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// CheckHandler handles GET /v1/check?hash={sha256}
// Looks up a hashed phone number and returns its risk score.
// Checks the allowlist first — if the number is allowlisted, returns risk 0.0 with label.
type CheckHandler struct {
	numberStore    *store.ScamNumberStore
	allowlistStore *store.AllowlistStore
	logger         *zap.Logger
}

func NewCheckHandler(numberStore *store.ScamNumberStore, allowlistStore *store.AllowlistStore, logger *zap.Logger) *CheckHandler {
	return &CheckHandler{
		numberStore:    numberStore,
		allowlistStore: allowlistStore,
		logger:         logger.Named("handler.check"),
	}
}

func (h *CheckHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	hash := r.URL.Query().Get("hash")
	if hash == "" {
		writeError(w, http.StatusBadRequest, "missing required parameter: hash")
		return
	}

	if len(hash) != 64 || !isHexString(hash) {
		writeError(w, http.StatusBadRequest, "invalid hash: must be a 64-character hex string (SHA-256)")
		return
	}

	ctx := r.Context()

	// Check allowlist first
	allowed, err := h.allowlistStore.IsAllowed(ctx, hash)
	if err != nil {
		h.logger.Warn("allowlist check error, proceeding with scam check", zap.Error(err))
	}
	if allowed {
		// Get the allowlist entry for label
		entries, _ := h.allowlistStore.GetAll(ctx)
		label := "Known Good"
		for _, e := range entries {
			if e.NumberHash == hash {
				label = e.Label
				break
			}
		}

		writeJSON(w, http.StatusOK, model.CheckResponse{
			Hash:            hash,
			RiskScore:       0.0,
			Risk:            0.0,
			Label:           label,
			Tags:            []string{"allowlisted"},
			Found:           true,
			Confidence:      1.0,
			IsConfirmed:     true,
			ReportCount:     0,
			Source:          "allowlist",
			SuggestedAction: "allow",
		})
		h.logger.Debug("number check: allowlisted", zap.String("hash_prefix", hash[:8]))
		return
	}

	// Check scam database
	sn, err := h.numberStore.GetByHash(ctx, hash)
	if err != nil && err.Error() != "record not found" {
		h.logger.Error("database lookup failed", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	resp := model.CheckResponse{
		Hash:         hash,
		RiskScore:    0.0,
		Risk:         0.0,
		Label:        "Not Found",
		Tags:         []string{},
		Found:        false,
		Confidence:   0.0,
		IsConfirmed:  false,
		ReportCount:  0,
		Source:       "",
	}

	if sn != nil {
		resp.RiskScore = sn.RiskScore
		resp.Risk = sn.RiskScore
		resp.ScamType = sn.ScamType
		resp.Label = sn.ScamType
		resp.Tags = []string{sn.ScamType}
		resp.Found = true
		resp.Source = sn.Source
		resp.Confidence = sn.RiskScore
		resp.ReportCount = sn.ReportCount
		resp.IsConfirmed = sn.RiskScore >= 0.8
		resp.SuggestedAction = "block"
		if !sn.FirstSeen.IsZero() {
			resp.FirstReported = sn.FirstSeen.Unix()
		}
	}

	writeJSON(w, http.StatusOK, resp)

	if sn != nil {
		h.logger.Info("number check: scam found",
			zap.String("hash_prefix", hash[:8]),
			zap.Float64("risk", resp.RiskScore),
			zap.String("scam_type", resp.ScamType),
		)
	} else {
		h.logger.Info("number check: no match",
			zap.String("hash_prefix", hash[:8]),
		)
	}
}
