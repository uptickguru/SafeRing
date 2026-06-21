package handler

import (
	"net/http"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/store"
)

// PrefixesHandler handles GET /v1/prefixes
// Returns known scammer phone number prefixes for offline caching.
type PrefixesHandler struct {
	prefixStore *store.ScamPrefixStore
	logger      *zap.Logger
}

func NewPrefixesHandler(prefixStore *store.ScamPrefixStore, logger *zap.Logger) *PrefixesHandler {
	return &PrefixesHandler{
		prefixStore: prefixStore,
		logger:      logger.Named("handler.prefixes"),
	}
}

func (h *PrefixesHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	prefixes, err := h.prefixStore.GetAll(ctx)
	if err != nil {
		h.logger.Error("failed to fetch prefixes", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "database error")
		return
	}

	// Build response with iOS-compatible fields
	respPrefixes := make([]struct {
		Prefix      string   `json:"prefix"`
		CountryCode string   `json:"country_code,omitempty"`
		RiskScore   float64  `json:"risk_score"`
		Risk        float64  `json:"risk"`
		ScamType    string   `json:"scam_type,omitempty"`
		ReportCount int      `json:"report_count,omitempty"`
		Count       int      `json:"count"`
		SamplesHashed int    `json:"samples_hashed,omitempty"`
		CommonTags  []string `json:"common_tags,omitempty"`
		LastUpdated string   `json:"last_updated,omitempty"`
	}, len(prefixes))

	for i, p := range prefixes {
		tags := []string{}
		if p.ScamType != "" {
			tags = []string{p.ScamType}
		}
		updated := ""
		if !p.LastUpdated.IsZero() {
			updated = p.LastUpdated.UTC().Format("2006-01-02T15:04:05Z")
		}
		respPrefixes[i] = struct {
			Prefix      string   `json:"prefix"`
			CountryCode string   `json:"country_code,omitempty"`
			RiskScore   float64  `json:"risk_score"`
			Risk        float64  `json:"risk"`
			ScamType    string   `json:"scam_type,omitempty"`
			ReportCount int      `json:"report_count,omitempty"`
			Count       int      `json:"count"`
			SamplesHashed int    `json:"samples_hashed,omitempty"`
			CommonTags  []string `json:"common_tags,omitempty"`
			LastUpdated string   `json:"last_updated,omitempty"`
		}{
			Prefix:        p.Prefix,
			CountryCode:   p.CountryCode,
			RiskScore:     p.RiskScore,
			Risk:          p.RiskScore,
			ScamType:      p.ScamType,
			ReportCount:   p.ReportCount,
			Count:         p.SamplesHashed,
			SamplesHashed: p.SamplesHashed,
			CommonTags:    tags,
			LastUpdated:   updated,
		}
	}

	// Get latest update timestamp
	var latestUpdate time.Time
	if len(prefixes) > 0 {
		latestUpdate = prefixes[0].LastUpdated
		for _, p := range prefixes[1:] {
			if p.LastUpdated.After(latestUpdate) {
				latestUpdate = p.LastUpdated
			}
		}
	}

	resp := struct {
		Prefixes  interface{} `json:"prefixes"`
		Total     int         `json:"total"`
		Updated   string      `json:"last_updated"`
		UpdatedAt int64       `json:"updated_at"`
	}{
		Prefixes:  respPrefixes,
		Total:     len(prefixes),
		Updated:   latestUpdate.UTC().Format("2006-01-02T15:04:05Z"),
		UpdatedAt: latestUpdate.Unix(),
	}

	writeJSON(w, http.StatusOK, resp)
}
