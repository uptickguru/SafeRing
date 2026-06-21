package handler

import (
	"encoding/json"
	"net/http"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// ReportHandler handles POST /v1/report
// Accepts user-submitted scam phone number reports (hash only, no PII).
type ReportHandler struct {
	reportStore *store.ScamReportStore
	logger      *zap.Logger
}

// NewReportHandler creates a new ReportHandler.
func NewReportHandler(reportStore *store.ScamReportStore, logger *zap.Logger) *ReportHandler {
	return &ReportHandler{
		reportStore: reportStore,
		logger:      logger.Named("handler.report"),
	}
}

// ServeHTTP handles POST /v1/report.
//
// Request Body:
//
//	{
//	  "hash": "<sha256 hex string>",
//	  "tag": "irs|tech-support|...",    // optional
//	  "timestamp": "2025-01-15T10:30:00Z"  // optional
//	}
//
// Response (201):
//
//	{
//	  "status": "accepted",
//	  "hash": "<sha256 hex string>"
//	}
//
// Rate limit: 20/min per IP.
// Zero PII: only the hash is stored; no IP, no device ID, no location.
func (h *ReportHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req model.ReportRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	if err := req.Validate(); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	report := &model.ScamReport{
		NumberHash: req.NumberHash,
		Tag:        req.Tag,
	}

	if req.Timestamp != "" {
		if t, err := time.Parse(time.RFC3339, req.Timestamp); err == nil {
			report.ReportedAt = t
		}
	}

	if err := h.reportStore.Insert(r.Context(), report); err != nil {
		h.logger.Error("failed to save report", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "failed to save report")
		return
	}

	h.logger.Info("scam report received",
		zap.String("hash_prefix", req.NumberHash[:8]),
		zap.String("tag", req.Tag),
	)

	writeJSON(w, http.StatusCreated, map[string]string{
		"status": "accepted",
		"hash":   req.NumberHash,
	})
}
