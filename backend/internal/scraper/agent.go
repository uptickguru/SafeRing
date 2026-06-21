// Package scraper implements the distributed scraping agent protocol.
//
// Architecture:
//
//	┌─────────────────────┐       ┌─────────────────────┐
//	│  Coordinator (this) │◄─────►│  Worker (R730)       │
//	│  - Task assignment   │ HTTP  │  - Scrapes sources   │
//	│  - Dedup + store     │ over  │  - Reports entries   │
//	│  - Throttle telemetry│ TLS   │  - Reports throttles │
//	│  - Agent registry    │       │  - Heartbeat         │
//	└─────────────────────┘       └─────────────────────┘
//
// Like crypto miners: always running, always checking for work,
// always reporting back hashrate (scrape rate) and difficulty (throttle state).
package scraper

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math/rand"
	"net"
	"net/http"
	"sync"
	"time"

	"golang.org/x/net/proxy"
	"go.uber.org/zap"
)

// AgentID is a unique identifier for a scraping agent instance.
type AgentID string

// AgentStatus describes the current state of an agent.
type AgentStatus string

const (
	AgentStatusIdle       AgentStatus = "idle"
	AgentStatusScraping   AgentStatus = "scraping"
	AgentStatusBackoff    AgentStatus = "backoff"
	AgentStatusCooldown   AgentStatus = "cooldown"
	AgentStatusOffline    AgentStatus = "offline"
)

// AgentInfo is the metadata an agent reports during registration and heartbeat.
type AgentInfo struct {
	ID             AgentID           `json:"id"`
	Hostname       string            `json:"hostname"`
	Version        string            `json:"version"`
	Status         AgentStatus       `json:"status"`
	Uptime         time.Duration     `json:"uptime"`
	LastHeartbeat  time.Time         `json:"last_heartbeat"`
	SourcesScraped map[string]int    `json:"sources_scraped"`
	TotalEntries   int64             `json:"total_entries"`
	Errors         int64             `json:"errors"`
	Throttles      []map[string]interface{} `json:"throttles,omitempty"`
	LoadLevel      string            `json:"load_level,omitempty"`
	LoadCPU        float64           `json:"load_cpu,omitempty"`
	LoadMemory     float64           `json:"load_memory,omitempty"`
}

// AgentReport is the payload an agent sends after completing a scrape.
type AgentReport struct {
	AgentID   AgentID          `json:"agent_id"`
	Source    string           `json:"source"`
	TaskID    string           `json:"task_id"`
	Entries   []ScrapedEntry   `json:"entries"`
	Duration  time.Duration    `json:"duration_ms"`
	Throttle  ThrottleState    `json:"throttle"`
	Success   bool             `json:"success"`
	Error     string           `json:"error,omitempty"`
	StartedAt time.Time        `json:"started_at"`
}

// AgentManager runs on the coordinator side, managing registered agents.
type AgentManager struct {
	mu       sync.RWMutex
	agents   map[AgentID]*AgentInfo
	logger   *zap.Logger
}

// NewAgentManager creates a new agent registry.
func NewAgentManager(logger *zap.Logger) *AgentManager {
	return &AgentManager{
		agents: make(map[AgentID]*AgentInfo),
		logger: logger.Named("scraper.agent-manager"),
	}
}

// Register adds or updates an agent in the registry.
func (m *AgentManager) Register(info AgentInfo) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if existing, ok := m.agents[info.ID]; ok {
		existing.Status = info.Status
		existing.LastHeartbeat = time.Now()
		existing.Uptime = info.Uptime
		existing.SourcesScraped = info.SourcesScraped
		existing.TotalEntries = info.TotalEntries
		existing.Errors = info.Errors
		m.logger.Debug("agent heartbeat", zap.String("agent", string(info.ID)))
	} else {
		info.LastHeartbeat = time.Now()
		m.agents[info.ID] = &info
		m.logger.Info("agent registered",
			zap.String("agent", string(info.ID)),
			zap.String("hostname", info.Hostname),
		)
	}
}

// Unregister removes an agent from the registry.
func (m *AgentManager) Unregister(id AgentID) {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.agents, id)
	m.logger.Info("agent unregistered", zap.String("agent", string(id)))
}

// List returns all currently registered agents.
func (m *AgentManager) List() []AgentInfo {
	m.mu.RLock()
	defer m.mu.RUnlock()

	infos := make([]AgentInfo, 0, len(m.agents))
	for _, info := range m.agents {
		infos = append(infos, *info)
	}
	return infos
}

// Get returns a specific agent by ID.
func (m *AgentManager) Get(id AgentID) (AgentInfo, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	info, ok := m.agents[id]
	if !ok {
		return AgentInfo{}, false
	}
	return *info, true
}

// Count returns the number of registered agents.
func (m *AgentManager) Count() int {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return len(m.agents)
}

// CleanupOffline removes agents that haven't heartbeated in a while.
func (m *AgentManager) CleanupOffline(timeout time.Duration) {
	m.mu.Lock()
	defer m.mu.Unlock()

	now := time.Now()
	for id, info := range m.agents {
		if now.Sub(info.LastHeartbeat) > timeout {
			info.Status = AgentStatusOffline
			m.logger.Warn("agent went offline",
				zap.String("agent", string(id)),
				zap.Duration("since_last_heartbeat", now.Sub(info.LastHeartbeat)),
			)
			delete(m.agents, id)
		}
	}
}

// DistributedAgent runs on a worker node, connecting to the coordinator.
// This is the "crypto miner" that runs on each machine.
type DistributedAgent struct {
	ID                AgentID
	CoordinatorURL    string
	Client            *http.Client
	ProxyClient       *http.Client // SOCKS5 proxy client (nil if no proxy)
	Throttler         *MultiSourceThrottler
	Sources           *SourceRegistry
	Logger            *zap.Logger
	Version           string
	proxyURL          string
	LoadMonitor       *LoadMonitor
	enableLoadMonitor bool

	// Internal state
	hostname       string
	startedAt      time.Time
	scrapeCount    map[string]int
	totalEntries   int64
	totalErrors    int64
	mu             sync.Mutex
	stopCh         chan struct{}
}

// DistributedAgentConfig configures a worker agent.
type DistributedAgentConfig struct {
	CoordinatorURL string
	AgentID        string
	Logger         *zap.Logger
	Version        string
	// SOCKS5 proxy URL (e.g. "socks5://127.0.0.1:9050").
	// When set, sources with UseProxy=true route through this proxy.
	// Empty string = no proxy, all sources use direct connection.
	Socks5Proxy string
	// Enable resource-aware scraping. When true, the agent monitors system
	// load and backs off when the host is busy ("you idle, we work you").
	// Default: true.
	EnableLoadMonitor bool
}

// newHTTPTransport creates a standard http.Transport with sensible defaults.
func newHTTPTransport() *http.Transport {
	return &http.Transport{
		MaxIdleConns:        10,
		IdleConnTimeout:     30 * time.Second,
		DisableCompression:  false,
		DialContext:         (&net.Dialer{
			Timeout:   30 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
	}
}

// newProxyHTTPTransport creates an http.Transport that routes through a SOCKS5 proxy.
func newProxyHTTPTransport(proxyURL string) (*http.Transport, error) {
	dialer, err := proxy.SOCKS5("tcp", proxyURL, nil, proxy.Direct)
	if err != nil {
		return nil, fmt.Errorf("socks5 dialer: %w", err)
	}
	return &http.Transport{
		MaxIdleConns:        10,
		IdleConnTimeout:     30 * time.Second,
		DisableCompression:  false,
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return dialer.(proxy.ContextDialer).DialContext(ctx, network, addr)
		},
	}, nil
}

// NewDistributedAgent creates a new scraper worker agent.
func NewDistributedAgent(cfg DistributedAgentConfig) *DistributedAgent {
	directTransport := newHTTPTransport()

	// Enable load monitor by default
	enableLoadMon := true
	if !cfg.EnableLoadMonitor {
		enableLoadMon = false
	}

	agent := &DistributedAgent{
		ID:             AgentID(cfg.AgentID),
		CoordinatorURL: cfg.CoordinatorURL,
		Client: &http.Client{
			Timeout:   60 * time.Second,
			Transport: directTransport,
		},
		Throttler:         NewMultiSourceThrottler(),
		Sources:           NewDefaultRegistry(),
		Logger:            cfg.Logger.Named("scraper.agent"),
		Version:           cfg.Version,
		proxyURL:          cfg.Socks5Proxy,
		LoadMonitor:       NewLoadMonitor(cfg.Logger),
		enableLoadMonitor:  enableLoadMon,
		hostname:          cfg.AgentID,
		startedAt:         time.Now(),
		scrapeCount:       make(map[string]int),
		stopCh:            make(chan struct{}),
	}

	// Set up proxy client if SOCKS5 configured
	if cfg.Socks5Proxy != "" {
		proxyTransport, err := newProxyHTTPTransport(cfg.Socks5Proxy)
		if err != nil {
			cfg.Logger.Warn("failed to setup SOCKS5 proxy, falling back to direct",
				zap.String("proxy", cfg.Socks5Proxy),
				zap.Error(err),
			)
		} else {
			agent.ProxyClient = &http.Client{
				Timeout:   120 * time.Second, // Proxy routes may be slower
				Transport: proxyTransport,
			}
			cfg.Logger.Info("SOCKS5 proxy configured",
				zap.String("proxy", cfg.Socks5Proxy),
			)
		}
	}

	return agent
}

// Run is the main agent loop — this never returns until ctx is cancelled.
// Like a miner: always has work, always checks in, adapts to conditions.
func (a *DistributedAgent) Run(ctx context.Context) error {
	// Register with coordinator
	if err := a.sendHeartbeat(ctx); err != nil {
		a.Logger.Warn("initial heartbeat failed, continuing anyway", zap.Error(err))
	}

	// Start load monitor in background
	if a.enableLoadMonitor {
		go a.LoadMonitor.Start(ctx, 5*time.Second)
		a.Logger.Info("load monitor enabled — agent yields when host is busy")
	}

	// Heartbeat ticker
	heartbeatTicker := time.NewTicker(30 * time.Second)
	defer heartbeatTicker.Stop()

	a.Logger.Info("agent started",
		zap.String("id", string(a.ID)),
		zap.String("coordinator", a.CoordinatorURL),
	)

	for {
		// Get load-adaptive scrape parameters
		interval := 15 * time.Second
		if a.enableLoadMonitor {
			level := a.LoadMonitor.Level()
			interval, _, _ = level.ScrapeParams()
			a.Logger.Debug("load state",
				zap.String("level", level.String()),
				zap.Duration("interval", interval),
			)
		}

		// Add jitter: +/- 25%
		jitter := time.Duration(float64(interval) * 0.25 * (rand.Float64()*2 - 1))
		interval += jitter

		scrapeTicker := time.NewTimer(interval)

		select {
		case <-ctx.Done():
			scrapeTicker.Stop()
			a.Logger.Info("agent stopping")
			return ctx.Err()

		case <-heartbeatTicker.C:
			scrapeTicker.Stop()
			if err := a.sendHeartbeat(ctx); err != nil {
				a.Logger.Warn("heartbeat failed", zap.Error(err))
			}

		case <-scrapeTicker.C:
			a.doScrapeCycle(ctx)
		}
	}
}

// doScrapeCycle checks for work and executes a scrape if available.
func (a *DistributedAgent) doScrapeCycle(ctx context.Context) {
	// Get a task from the coordinator
	task, err := a.requestTask(ctx)
	if err != nil {
		// No task or coordinator unreachable — scrape autonomously
		a.scrapeAutonomously(ctx)
		return
	}
	if task == nil {
		return // No work available
	}

	// Execute the assigned task
	a.executeTask(ctx, task)
}

// requestTask asks the coordinator for work.
func (a *DistributedAgent) requestTask(ctx context.Context) (*ScrapeTask, error) {
	req, err := http.NewRequestWithContext(ctx, "GET",
		a.CoordinatorURL+"/v1/agent/task?agent_id="+string(a.ID), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Agent-ID", string(a.ID))

	resp, err := a.Client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNoContent {
		return nil, nil // No task available
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("coordinator returned %d", resp.StatusCode)
	}

	var task ScrapeTask
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		return nil, err
	}
	return &task, nil
}

// executeTask runs a scrape for a specific source.
func (a *DistributedAgent) executeTask(ctx context.Context, task *ScrapeTask) {
	a.mu.Lock()
	a.scrapeCount[task.Source]++
	a.mu.Unlock()

	start := time.Now()
	a.Logger.Info("executing scrape task",
		zap.String("source", task.Source),
		zap.String("task_id", task.TaskID),
	)

	// Find the source
	source, ok := a.Sources.Get(task.Source)
	if !ok {
		a.Logger.Error("unknown source", zap.String("source", task.Source))
		return
	}

	// Get throttler for this source
	throttler := a.Throttler.Get(task.Source)

	// Wait for throttle clearance
	throttler.Wait(ctx)

	// Try each strategy
	var entries []ScrapedEntry
	var success bool
	var lastErr error

	for i := task.StrategyIdx; i < len(source.Strategies) && !success; i++ {
		strategy := source.Strategies[i]

		entries, lastErr = a.scrapeWithStrategy(ctx, source, strategy)
		if lastErr == nil && len(entries) > 0 {
			success = true
			break
		}
	}

	duration := time.Since(start)
	var reportErr string
	if lastErr != nil {
		reportErr = lastErr.Error()
	}

	// Report back to coordinator
	report := AgentReport{
		AgentID:   a.ID,
		Source:    task.Source,
		TaskID:    task.TaskID,
		Entries:   entries,
		Duration:  duration,
		Throttle:  throttler.State(),
		Success:   success,
		Error:     reportErr,
		StartedAt: start,
	}

	if err := a.sendReport(ctx, report); err != nil {
		a.Logger.Warn("failed to send report", zap.Error(err))
	}

	a.mu.Lock()
	a.totalEntries += int64(len(entries))
	if !success {
		a.totalErrors++
	}
	a.mu.Unlock()
}

// clientForStrategy returns the appropriate HTTP client for a given strategy.
// Uses the proxy client if UseProxy is true and a proxy is configured.
func (a *DistributedAgent) clientForStrategy(strategy ScrapeStrategy) *http.Client {
	useProxy := strategy.UseProxy
	if !useProxy {
		return a.Client
	}
	if a.ProxyClient != nil {
		return a.ProxyClient
	}
	a.Logger.Debug("strategy marked for proxy but no proxy configured, using direct",
		zap.String("url", strategy.URL[:min(len(strategy.URL), 60)]),
	)
	return a.Client
}

// scrapeWithStrategy attempts to scrape using a specific strategy.
func (a *DistributedAgent) scrapeWithStrategy(ctx context.Context, source SourceDescriptor, strategy ScrapeStrategy) ([]ScrapedEntry, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", strategy.URL, nil)
	if err != nil {
		return nil, err
	}

	for k, v := range strategy.Headers {
		req.Header.Set(k, v)
	}

	throttler := a.Throttler.Get(source.Name)
	client := a.clientForStrategy(strategy)

	resp, err := client.Do(req)
	if err != nil {
		throttler.RecordError(err)
		return nil, fmt.Errorf("fetch %s: %w", strategy.URL, err)
	}
	defer resp.Body.Close()

	// Feed response to throttler (sniff throttles!)
	throttler.RecordResponse(resp)

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%s returned %d", strategy.URL, resp.StatusCode)
	}

	body, err := io.ReadAll(io.LimitReader(resp.Body, 5*1024*1024))
	if err != nil {
		return nil, fmt.Errorf("read body: %w", err)
	}

	// Parse based on content type
	var entries []ScrapedEntry
	switch strategy.Type {
	case "json":
		entries, err = parseJSONEntries(body, source)
	case "html":
		entries, err = parseHTMLEntries(body, source, strategy)
	default:
		return nil, fmt.Errorf("unsupported strategy type: %s", strategy.Type)
	}

	if err != nil {
		return nil, err
	}

	// Apply source credibility
	for i := range entries {
		if entries[i].RiskScore == 0 {
			entries[i].RiskScore = source.Credibility
		}
	}

	// Limit records
	if len(entries) > source.MaxRecords {
		entries = entries[:source.MaxRecords]
	}

	return entries, nil
}

// scrapeAutonomously runs a scrape without coordinator assignment.
// Used when the coordinator is unreachable — agents work independently.
func (a *DistributedAgent) scrapeAutonomously(ctx context.Context) {
	sources := a.Sources.EnabledSources()
	if len(sources) == 0 {
		return
	}

	// Pick a random source weighted by priority
	source := pickWeightedSource(sources)

	throttler := a.Throttler.Get(source.Name)
	if throttler.State().Phase == "cooldown" || throttler.State().Phase == "backoff" {
		// Source is throttled, skip this cycle
		return
	}

	// Execute scrape
	for _, strategy := range source.Strategies {
		throttler.Wait(ctx)
		entries, err := a.scrapeWithStrategy(ctx, source, strategy)
		if err != nil {
			a.Logger.Debug("autonomous scrape failed",
				zap.String("source", source.Name),
				zap.Error(err),
			)
			continue
		}

		if len(entries) > 0 {
			a.mu.Lock()
			a.totalEntries += int64(len(entries))
			a.scrapeCount[source.Name]++
			a.mu.Unlock()

			// Try to submit to coordinator
			report := AgentReport{
				AgentID:  a.ID,
				Source:   source.Name,
				Entries:  entries,
				Throttle: throttler.State(),
				Success:  true,
			}
			a.sendReport(ctx, report) // Best-effort
		}
		break
	}
}

// sendHeartbeat registers/updates the agent with the coordinator.
func (a *DistributedAgent) sendHeartbeat(ctx context.Context) error {
	a.mu.Lock()
	info := AgentInfo{
		ID:             a.ID,
		Hostname:       a.hostname,
		Version:        a.Version,
		Status:         AgentStatusIdle,
		Uptime:         time.Since(a.startedAt),
		LastHeartbeat:  time.Now(),
		SourcesScraped: copyMap(a.scrapeCount),
		TotalEntries:   a.totalEntries,
		Errors:         a.totalErrors,
		Throttles:      a.Throttler.AllMetrics(),
	}
	if a.enableLoadMonitor {
		info.LoadLevel = a.LoadMonitor.Level().String()
		l := a.LoadMonitor.Load()
		info.LoadCPU = l.CPUPercent
		info.LoadMemory = l.MemoryPercent
	}
	a.mu.Unlock()

	body, err := json.Marshal(info)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, "POST",
		a.CoordinatorURL+"/v1/agent/heartbeat", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := a.Client.Do(req)
	if err != nil {
		return err
	}
	resp.Body.Close()
	return nil
}

// sendReport submits a scrape report to the coordinator.
func (a *DistributedAgent) sendReport(ctx context.Context, report AgentReport) error {
	body, err := json.Marshal(report)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, "POST",
		a.CoordinatorURL+"/v1/agent/report", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := a.Client.Do(req)
	if err != nil {
		return err
	}
	resp.Body.Close()
	return nil
}

// pickWeightedSource selects a source with priority-based weighting.
func pickWeightedSource(sources []SourceDescriptor) SourceDescriptor {
	// Weight: Critical=4, High=3, Normal=2, Low=1, Opportunistic=1
	weights := map[SourcePriority]int{
		PriorityCritical:       4,
		PriorityHigh:           3,
		PriorityNormal:         2,
		PriorityLow:            1,
		PriorityOpportunistic:  1,
	}

	totalWeight := 0
	for _, s := range sources {
		totalWeight += weights[s.Priority]
	}

	r := rand.Intn(totalWeight)
	for _, s := range sources {
		r -= weights[s.Priority]
		if r < 0 {
			return s
		}
	}

	return sources[0]
}

// copyMap shallow-copies a map.
func copyMap(m map[string]int) map[string]int {
	out := make(map[string]int, len(m))
	for k, v := range m {
		out[k] = v
	}
	return out
}

// parseJSONEntries parses JSON responses (like Reddit) into entries.
func parseJSONEntries(body []byte, source SourceDescriptor) ([]ScrapedEntry, error) {
	// Try to detect the response format
	var raw interface{}
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("invalid json: %w", err)
	}

	// Create a temporary RedditScraper for JSON parsing
	switch source.Name {
	case "reddit-r-scams", "reddit-r-scamnumbers", "reddit-r-phonescam":
		return parseRedditJSON(body)
	default:
		// Generic JSON array of entries
		return parseJSONArray(body)
	}
}

// parseJSONArray tries to parse a generic JSON array of record-like objects.
func parseJSONArray(body []byte) ([]ScrapedEntry, error) {
	var records []map[string]interface{}
	if err := json.Unmarshal(body, &records); err != nil {
		return nil, fmt.Errorf("not a JSON array: %w", err)
	}

	var entries []ScrapedEntry
	now := time.Now()

	for _, r := range records {
		phone := ""
		if p, ok := r["phone_number"].(string); ok {
			phone = p
		} else if p, ok := r["phone"].(string); ok {
			phone = p
		} else if p, ok := r["number"].(string); ok {
			phone = p
		}

		cleaned := CleanPhoneNumber(phone)
		if cleaned == "" {
			continue
		}

		scamType, _ := r["scam_type"].(string)
		riskRaw, _ := r["risk_score"].(float64)

		entries = append(entries, ScrapedEntry{
			NumberHash: HashPhoneNumber(cleaned),
			Source:     "generic",
			ScamType:   NormalizeScamType(scamType),
			RiskScore:  riskRaw,
			ReportedAt: now,
		})
	}

	return entries, nil
}

// parseHTMLEntries parses HTML pages into entries.
// For now, uses regex-based phone number extraction.
func parseHTMLEntries(body []byte, source SourceDescriptor, strategy ScrapeStrategy) ([]ScrapedEntry, error) {
	// Simple regex-based phone extraction from HTML
	// In production, use goquery for proper HTML parsing
	matches := phonePattern.FindAllString(string(body), -1)

	seen := make(map[string]bool)
	var entries []ScrapedEntry
	now := time.Now()

	for _, phone := range matches {
		cleaned := CleanPhoneNumber(phone)
		if cleaned == "" {
			continue
		}

		hash := HashPhoneNumber(cleaned)
		if seen[hash] {
			continue
		}
		seen[hash] = true

		entries = append(entries, ScrapedEntry{
			NumberHash: hash,
			Source:     source.Name,
			ScamType:   "other",
			RiskScore:  source.Credibility,
			ReportedAt: now,
		})

		if len(entries) >= source.MaxRecords {
			break
		}
	}

	return entries, nil
}
