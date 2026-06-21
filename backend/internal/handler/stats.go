package handler

import (
	"net/http"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/scraper"
	"github.com/safering/backend/internal/store"
)

// StatsHandler handles GET /v1/stats
// Returns anonymous aggregate statistics about scam reports and database state.
// No identifying information is returned — purely aggregate counts.
type StatsHandler struct {
	reportStore *store.ScamReportStore
	numberStore *store.ScamNumberStore
	prefixStore *store.ScamPrefixStore
	aggregator  *scraper.Aggregator
	logger      *zap.Logger
}

// NewStatsHandler creates a new StatsHandler.
func NewStatsHandler(
	reportStore *store.ScamReportStore,
	numberStore *store.ScamNumberStore,
	prefixStore *store.ScamPrefixStore,
	aggregator *scraper.Aggregator,
	logger *zap.Logger,
) *StatsHandler {
	return &StatsHandler{
		reportStore: reportStore,
		numberStore: numberStore,
		prefixStore: prefixStore,
		aggregator:  aggregator,
		logger:      logger.Named("handler.stats"),
	}
}

// StatsResponse is the JSON response for GET /v1/stats.
type StatsResponse struct {
	TotalReports      int            `json:"total_reports"`
	TotalScamNumbers  int            `json:"total_scam_numbers"`
	TotalPrefixes     int            `json:"total_prefixes"`
	TopScams          map[string]int `json:"top_scams"`
	LastScrapeRun     string         `json:"last_scrape_run,omitempty"`
	ScrapeStats       interface{}    `json:"scrape_stats,omitempty"`
	ScamNumbersByType map[string]int `json:"scam_numbers_by_type,omitempty"`
}

// ServeHTTP handles GET /v1/stats.
func (h *StatsHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Fetch all stats in parallel using goroutines
	type result struct {
		key   string
		value interface{}
	}

	ch := make(chan result, 5)

	go func() {
		count, err := h.reportStore.GetTotalCount(r.Context())
		if err != nil {
			h.logger.Error("failed to get report count", zap.Error(err))
			ch <- result{"report_count", 0}
			return
		}
		ch <- result{"report_count", count}
	}()

	go func() {
		count, err := h.numberStore.GetCount(r.Context())
		if err != nil {
			h.logger.Error("failed to get number count", zap.Error(err))
			ch <- result{"number_count", 0}
			return
		}
		ch <- result{"number_count", count}
	}()

	go func() {
		count, err := h.prefixStore.GetCount(r.Context())
		if err != nil {
			h.logger.Error("failed to get prefix count", zap.Error(err))
			ch <- result{"prefix_count", 0}
			return
		}
		ch <- result{"prefix_count", count}
	}()

	go func() {
		counts, err := h.reportStore.GetCountByTag(r.Context())
		if err != nil {
			h.logger.Error("failed to get tag counts", zap.Error(err))
			ch <- result{"tag_counts", map[string]int{}}
			return
		}
		ch <- result{"tag_counts", counts}
	}()

	// Collect results
	results := make(map[string]interface{}, 5)
	for i := 0; i < 4; i++ {
		r := <-ch
		results[r.key] = r.value
	}

	reportCount, _ := results["report_count"].(int)
	numberCount, _ := results["number_count"].(int)
	prefixCount, _ := results["prefix_count"].(int)
	tagCounts, _ := results["tag_counts"].(map[string]int)

	// Build response
	resp := StatsResponse{
		TotalReports:     reportCount,
		TotalScamNumbers: numberCount,
		TotalPrefixes:    prefixCount,
		TopScams:         tagCounts,
	}

	// Add scrape stats if aggregator is available
	if h.aggregator != nil {
		if !h.aggregator.LastRun().IsZero() {
			resp.LastScrapeRun = h.aggregator.LastRun().Format("2006-01-02T15:04:05Z")
		}
		resp.ScrapeStats = h.aggregator.Stats()
	}

	writeJSON(w, http.StatusOK, resp)
}
