package scraper

import (
	"encoding/json"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/go-chi/chi/v5"
	"go.uber.org/zap"

	"github.com/safering/backend/internal/store"
)

// Coordinator manages the distributed scraping fleet.
// It's the "mining pool" — agents check in for work, report findings,
// and get telemetry back.
type Coordinator struct {
	agentManager    *AgentManager
	throttler       *MultiSourceThrottler
	sourceRegistry  *SourceRegistry
	numberStore     *store.ScamNumberStore
	prefixStore     *store.ScamPrefixStore
	aggregator      *Aggregator
	logger          *zap.Logger

	// Task queue
	taskQueue []ScrapeTask
	taskMu    sync.Mutex
	taskSeq   int64

	// Report buffer — batch writes to DB
	reportBuffer []AgentReport
	reportMu     sync.Mutex
}

// NewCoordinator creates the scraper coordinator.
func NewCoordinator(
	numberStore *store.ScamNumberStore,
	prefixStore *store.ScamPrefixStore,
	aggregator *Aggregator,
	logger *zap.Logger,
) *Coordinator {
	return &Coordinator{
		agentManager:   NewAgentManager(logger),
		throttler:      NewMultiSourceThrottler(),
		sourceRegistry: NewDefaultRegistry(),
		numberStore:    numberStore,
		prefixStore:    prefixStore,
		aggregator:     aggregator,
		logger:         logger.Named("scraper.coordinator"),
	}
}

// Routes registers coordinator HTTP endpoints on a chi router.
// Mount at /v1/agent for the agent communication protocol.
func (c *Coordinator) Routes(r chi.Router) {
	r.Post("/heartbeat", c.handleHeartbeat)
	r.Get("/task", c.handleGetTask)
	r.Post("/report", c.handleReport)
	r.Get("/sources", c.handleListSources)
	r.Get("/agents", c.handleListAgents)
	r.Get("/stats", c.handleStats)
}

// handleHeartbeat receives agent heartbeats.
func (c *Coordinator) handleHeartbeat(w http.ResponseWriter, r *http.Request) {
	var info AgentInfo
	if err := json.NewDecoder(r.Body).Decode(&info); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return
	}
	c.agentManager.Register(info)
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// handleGetTask assigns a scrape task to a requesting agent.
// Returns 204 No Content if no work is available.
func (c *Coordinator) handleGetTask(w http.ResponseWriter, r *http.Request) {
	c.taskMu.Lock()
	defer c.taskMu.Unlock()

	if len(c.taskQueue) == 0 {
		// Generate tasks from enabled sources if queue is empty
		c.refillTaskQueue()
	}

	if len(c.taskQueue) == 0 {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	// Pop front
	task := c.taskQueue[0]
	c.taskQueue = c.taskQueue[1:]

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(task)
}

// handleReport receives scrape results from agents.
func (c *Coordinator) handleReport(w http.ResponseWriter, r *http.Request) {
	var report AgentReport
	if err := json.NewDecoder(r.Body).Decode(&report); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return
	}

	c.reportMu.Lock()
	c.reportBuffer = append(c.reportBuffer, report)
	c.reportMu.Unlock()

	// Submit entries to aggregator for processing
	if report.Success && len(report.Entries) > 0 {
		entries := make([]ScrapedEntry, len(report.Entries))
		copy(entries, report.Entries)

		// Process in background
		go func() {
			ctx := r.Context()
			newCount, err := c.aggregator.processEntries(ctx, entries)
			if err != nil {
				c.logger.Warn("failed to process agent entries",
					zap.String("agent", string(report.AgentID)),
					zap.String("source", report.Source),
					zap.Error(err),
				)
				return
			}
			c.logger.Info("agent report processed",
				zap.String("agent", string(report.AgentID)),
				zap.String("source", report.Source),
				zap.Int("entries", len(entries)),
				zap.Int("new", newCount),
			)
		}()
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// handleListSources returns all known sources and their throttle states.
func (c *Coordinator) handleListSources(w http.ResponseWriter, r *http.Request) {
	sources := c.sourceRegistry.EnabledSources()
	throttles := c.throttler.AllStates()

	resp := struct {
		Sources   []SourceDescriptor            `json:"sources"`
		Throttles map[string]ThrottleState      `json:"throttles"`
		Agents    int                           `json:"active_agents"`
	}{
		Sources:   sources,
		Throttles: throttles,
		Agents:    c.agentManager.Count(),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

// handleListAgents returns all registered agents.
func (c *Coordinator) handleListAgents(w http.ResponseWriter, r *http.Request) {
	agents := c.agentManager.List()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(agents)
}

// handleStats returns coordinator telemetry.
func (c *Coordinator) handleStats(w http.ResponseWriter, r *http.Request) {
	agentMetrics := c.agentManager.List()
	throttleMetrics := c.throttler.AllMetrics()

	resp := struct {
		ActiveAgents int                      `json:"active_agents"`
		Agents       []AgentInfo              `json:"agents"`
		Throttles    []map[string]interface{} `json:"throttles"`
		QueueDepth   int                      `json:"queue_depth"`
		SourcesTotal int                      `json:"sources_total"`
	}{
		ActiveAgents: len(agentMetrics),
		Agents:       agentMetrics,
		Throttles:    throttleMetrics,
		QueueDepth:   len(c.taskQueue),
		SourcesTotal: len(c.sourceRegistry.EnabledSources()),
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

// refillTaskQueue populates the task queue from enabled sources.
func (c *Coordinator) refillTaskQueue() {
	sources := c.sourceRegistry.EnabledSources()
	for _, s := range sources {
		// Check throttle state before queueing
		state := c.throttler.Get(s.Name).State()
		if state.Phase == "cooldown" || state.Phase == "backoff" {
			c.logger.Debug("skipping throttled source",
				zap.String("source", s.Name),
				zap.String("phase", state.Phase),
			)
			continue
		}

		taskSeq := atomic.AddInt64(&c.taskSeq, 1)
		task := ScrapeTask{
			Source:      s.Name,
			StrategyIdx: 0,
			Priority:    int(s.Priority),
			AssignedAt:  time.Now(),
			Deadline:    time.Now().Add(5 * time.Minute),
			TaskID:      string(s.Name) + "-" + time.Now().Format("20060102150405"),
		}
		_ = taskSeq
		c.taskQueue = append(c.taskQueue, task)
	}
}

// FlushReports processes all buffered reports.
// Call periodically.
func (c *Coordinator) FlushReports(ctx interface{ Done() <-chan struct{} }) {
	c.reportMu.Lock()
	buffer := c.reportBuffer
	c.reportBuffer = nil
	c.reportMu.Unlock()

	c.logger.Debug("flushed reports", zap.Int("count", len(buffer)))
}
