package handler

import (
	"net/http"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// CheckHandler handles GET /v1/check?hash={sha256}
// Looks up a hashed phone number and returns its risk score.
type CheckHandler struct {
	numberStore *store.ScamNumberStore
	logger      *zap.Logger
}

func NewCheckHandler(numberStore *store.ScamNumberStore, logger *zap.Logger) *CheckHandler {
	return &CheckHandler{
		numberStore: numberStore,
		logger:      logger.Named("handler.check"),
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

	sn, err := h.numberStore.GetByHash(r.Context(), hash)
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

	// Log the check for analytics
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
