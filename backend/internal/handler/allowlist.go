package handler

import (
	"encoding/json"
	"net/http"

	"github.com/go-chi/chi/v5"
	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// AllowlistHandler provides admin CRUD for the allowlist.
//
// GET  /v1/admin/allowlist          — list all entries
// POST /v1/admin/allowlist          — add entry
// DELETE /v1/admin/allowlist/{hash} — remove entry
type AllowlistHandler struct {
	allowStore *store.AllowlistStore
	logger     *zap.Logger
}

// NewAllowlistHandler creates the handler.
func NewAllowlistHandler(allowStore *store.AllowlistStore, logger *zap.Logger) *AllowlistHandler {
	return &AllowlistHandler{
		allowStore: allowStore,
		logger:     logger.Named("handler.allowlist"),
	}
}

// --- GET /v1/admin/allowlist ---

type allowlistListResponse struct {
	Entries []*model.AllowlistEntry `json:"entries"`
	Total   int                     `json:"total"`
}

func (h *AllowlistHandler) List(w http.ResponseWriter, r *http.Request) {
	entries, err := h.allowStore.GetAll(r.Context())
	if err != nil {
		h.logger.Error("failed to list allowlist", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	writeJSON(w, http.StatusOK, allowlistListResponse{
		Entries: entries,
		Total:   len(entries),
	})
}

// --- POST /v1/admin/allowlist ---

type allowlistAddRequest struct {
	Hash  string `json:"hash"`   // SHA-256 hex hash (required)
	E164  string `json:"e164"`   // Original number (optional, for display)
	Label string `json:"label"`  // Human label: "Chase Bank", "SignalWire IVR"
	Source string `json:"source"` // "manual", "bank", "government", etc.
}

func (h *AllowlistHandler) Add(w http.ResponseWriter, r *http.Request) {
	var req allowlistAddRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}
	defer r.Body.Close()

	if req.Hash == "" {
		writeError(w, http.StatusBadRequest, "missing required field: hash")
		return
	}
	if len(req.Hash) != 64 || !isHexString(req.Hash) {
		writeError(w, http.StatusBadRequest, "invalid hash: must be a 64-character hex string")
		return
	}
	if req.Label == "" {
		req.Label = "Unknown"
	}
	if req.Source == "" {
		req.Source = model.AllowlistSourceManual
	}

	entry := &model.AllowlistEntry{
		NumberHash: req.Hash,
		E164:       req.E164,
		Label:      req.Label,
		Source:     req.Source,
	}

	if err := h.allowStore.Add(r.Context(), entry); err != nil {
		h.logger.Error("failed to add allowlist entry", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	h.logger.Info("allowlist entry added",
		zap.String("hash_prefix", req.Hash[:8]),
		zap.String("label", req.Label),
		zap.String("source", req.Source))

	writeJSON(w, http.StatusCreated, entry)
}

// --- DELETE /v1/admin/allowlist/{hash} ---

func (h *AllowlistHandler) Remove(w http.ResponseWriter, r *http.Request) {
	hash := chi.URLParam(r, "hash")
	if hash == "" {
		writeError(w, http.StatusBadRequest, "missing hash in URL")
		return
	}

	if len(hash) != 64 || !isHexString(hash) {
		writeError(w, http.StatusBadRequest, "invalid hash")
		return
	}

	if err := h.allowStore.Remove(r.Context(), hash); err != nil {
		h.logger.Error("failed to remove allowlist entry", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	h.logger.Info("allowlist entry removed", zap.String("hash_prefix", hash[:8]))
	writeJSON(w, http.StatusOK, map[string]string{"status": "removed"})
}

// --- POST /v1/admin/allowlist/sync — trigger SignalWire sync on demand ---

// This would be called by a cron job or admin button.
// The actual sync logic is in scraper.SignalWireSync; this endpoint just returns
// the current allowlist count so the admin can verify.
func (h *AllowlistHandler) Stats(w http.ResponseWriter, r *http.Request) {
	count, err := h.allowStore.GetCount(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"allowlist_count": count,
	})
}
