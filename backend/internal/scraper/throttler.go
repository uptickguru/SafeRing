// Package scraper provides a distributed, throttle-aware scraping system
// for gathering scam phone number intel from public sources.
//
// The throttler behaves like a crypto mining agent — always connected,
// constantly probing rate limits, adapting dynamically, never truly idle.
package scraper

import (
	"context"
	"math"
	"math/rand"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// ThrottleState tracks the real-time throttle status for a single source.
// Every 429, 503, or Retry-After response updates this state.
// Workers use it to decide when to probe next.
type ThrottleState struct {
	// Last status code we got
	LastStatusCode   int           `json:"last_status"`
	// How long the source wants us to wait (from Retry-After header)
	RetryAfter       time.Duration `json:"retry_after"`
	// Current backoff duration (exponential, with jitter)
	Backoff          time.Duration `json:"backoff"`
	// When we're allowed to request again
	NextAllowedAt    time.Time     `json:"next_allowed_at"`
	// Consecutive errors (429/503/timeout)
	ConsecutiveErrors int          `json:"consecutive_errors"`
	// Total requests made
	TotalRequests    int64         `json:"total_requests"`
	// Total throttled responses received
	TotalThrottled   int64         `json:"total_throttled"`
	// When this state was last updated
	LastUpdated      time.Time     `json:"last_updated"`
	// Current phase: "probing" | "backoff" | "normal" | "cooldown"
	Phase            string        `json:"phase"`
}

// ThrottlerConfig defines per-source throttle parameters.
type ThrottlerConfig struct {
	// Name of the source (e.g. "ftc", "bbb", "reddit")
	Name string
	// Base delay between requests in normal operation
	BaseDelay time.Duration
	// Minimum delay (floor)
	MinDelay time.Duration
	// Maximum backoff (cap)
	MaxBackoff time.Duration
	// Token bucket: max burst size
	Burst int
	// Token bucket: tokens per second refill
	TokensPerSecond float64
	// Max consecutive errors before entering cooldown
	MaxConsecutiveErrors int
	// How long to stay in cooldown before retry
	CooldownDuration time.Duration
	// Jitter factor (0.0 - 1.0, fraction of delay to randomize)
	JitterFactor float64
}

// DefaultThrottlerConfig returns sensible defaults for a public API scraper.
func DefaultThrottlerConfig(name string) ThrottlerConfig {
	return ThrottlerConfig{
		Name:                 name,
		BaseDelay:            2 * time.Second,
		MinDelay:             500 * time.Millisecond,
		MaxBackoff:           30 * time.Minute,
		Burst:                10,
		TokensPerSecond:      0.5, // 1 request per 2 seconds average
		MaxConsecutiveErrors: 5,
		CooldownDuration:     15 * time.Minute,
		JitterFactor:         0.3,
	}
}

// SourceThrottler manages rate limiting for a single data source.
// It's the "crypto miner" heart — always checking, adapting, never sleeping.
type SourceThrottler struct {
	cfg       ThrottlerConfig
	state     ThrottleState
	mu        sync.RWMutex
	tokens    float64
	lastRefill time.Time
	stopCh    chan struct{}
}

// NewSourceThrottler creates a new throttle manager for a source.
func NewSourceThrottler(cfg ThrottlerConfig) *SourceThrottler {
	t := &SourceThrottler{
		cfg:        cfg,
		state: ThrottleState{
			Phase: "probing",
		},
		tokens:     float64(cfg.Burst),
		lastRefill: time.Now(),
		stopCh:     make(chan struct{}),
	}
	return t
}

// Wait blocks until a request is allowed, respecting backoff and token bucket.
// Returns the actual delay waited. This is the core scheduler — it never returns
// an error, it just waits. Like a miner checking for the next block.
func (t *SourceThrottler) Wait(ctx context.Context) time.Duration {
	t.mu.Lock()

	now := time.Now()
	state := t.state

	// Check if we're in active backoff
	if now.Before(state.NextAllowedAt) {
		waitDur := state.NextAllowedAt.Sub(now)
		t.mu.Unlock()

		// Add jitter
		jitter := time.Duration(float64(waitDur) * t.cfg.JitterFactor * rand.Float64())
		waitDur += jitter

		select {
		case <-ctx.Done():
			return 0
		case <-time.After(waitDur):
			return waitDur
		}
	}

	// Refill token bucket
	t.refillTokens(now)

	// If no tokens available, wait for one
	if t.tokens < 1.0 {
		waitDur := time.Duration(float64(time.Second) / t.cfg.TokensPerSecond)
		t.mu.Unlock()

		jitter := time.Duration(float64(waitDur) * t.cfg.JitterFactor * rand.Float64())
		waitDur += jitter

		select {
		case <-ctx.Done():
			return 0
		case <-time.After(waitDur):
			return waitDur
		}
	}

	// Consume a token
	t.tokens--
	t.state.TotalRequests++
	atomic.AddInt64(&t.state.TotalRequests, 0) // just to keep atomic for stats
	t.mu.Unlock()

	// Base delay between requests
	baseDelay := t.cfg.BaseDelay
	jitter := time.Duration(float64(baseDelay) * t.cfg.JitterFactor * rand.Float64())
	baseDelay += jitter

	select {
	case <-ctx.Done():
		return 0
	case <-time.After(baseDelay):
		return baseDelay
	}
}

// RecordResponse feeds a server response into the throttle state.
// This is how we "sniff" throttles — every response updates our model.
// Returns the recommended wait time.
func (t *SourceThrottler) RecordResponse(resp *http.Response) time.Duration {
	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	t.state.LastStatusCode = resp.StatusCode
	t.state.LastUpdated = now

	switch {
	case resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode == 429:
		// Throttled — this is the key signal
		t.state.TotalThrottled++
		t.state.ConsecutiveErrors++
		t.state.Phase = "backoff"

		// Parse Retry-After header
		retryAfter := parseRetryAfter(resp)
		t.state.RetryAfter = retryAfter

		// Exponential backoff: base * 2^errors + Retry-After
		backoff := t.cfg.BaseDelay * time.Duration(math.Pow(2, float64(t.state.ConsecutiveErrors)))
		if retryAfter > backoff {
			backoff = retryAfter
		}
		if backoff > t.cfg.MaxBackoff {
			backoff = t.cfg.MaxBackoff
		}
		// Add jitter: +/- 30%
		jitter := time.Duration(float64(backoff) * t.cfg.JitterFactor * (rand.Float64()*2 - 1))
		backoff += jitter
		if backoff < t.cfg.MinDelay {
			backoff = t.cfg.MinDelay
		}
		t.state.Backoff = backoff
		t.state.NextAllowedAt = now.Add(backoff)

	case resp.StatusCode == http.StatusServiceUnavailable || resp.StatusCode >= 500:
		// Server error — back off moderately
		t.state.ConsecutiveErrors++
		t.state.Phase = "backoff"
		backoff := t.cfg.BaseDelay * time.Duration(math.Pow(1.5, float64(t.state.ConsecutiveErrors)))
		if backoff > t.cfg.MaxBackoff {
			backoff = t.cfg.MaxBackoff
		}
		t.state.Backoff = backoff
		t.state.NextAllowedAt = now.Add(backoff)

	case resp.StatusCode == http.StatusForbidden || resp.StatusCode == 401:
		// Auth/permission error — cooldown longer, might need credential refresh
		t.state.ConsecutiveErrors++
		t.state.Phase = "cooldown"
		backoff := t.cfg.CooldownDuration
		t.state.Backoff = backoff
		t.state.NextAllowedAt = now.Add(backoff)

	default:
		// Success or client error — reset backoff
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			t.state.ConsecutiveErrors = 0
			t.state.Phase = "normal"
			t.state.Backoff = t.cfg.BaseDelay
			t.state.NextAllowedAt = now
		}
	}

	return t.state.Backoff
}

// RecordError records a non-HTTP error (timeout, DNS failure, connection refused).
func (t *SourceThrottler) RecordError(err error) {
	t.mu.Lock()
	defer t.mu.Unlock()

	t.state.ConsecutiveErrors++
	t.state.LastUpdated = time.Now()

	backoff := t.cfg.BaseDelay * time.Duration(math.Pow(2, float64(t.state.ConsecutiveErrors)))
	if backoff > t.cfg.MaxBackoff {
		backoff = t.cfg.MaxBackoff
	}
	t.state.Backoff = backoff
	t.state.NextAllowedAt = time.Now().Add(backoff)
	t.state.Phase = "backoff"
}

// State returns a snapshot of the current throttle state.
func (t *SourceThrottler) State() ThrottleState {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return t.state
}

// ResetBackoff resets the backoff and consecutive error counter.
// Used when we want to force a probe.
func (t *SourceThrottler) ResetBackoff() {
	t.mu.Lock()
	defer t.mu.Unlock()
	t.state.ConsecutiveErrors = 0
	t.state.Backoff = t.cfg.BaseDelay
	t.state.NextAllowedAt = time.Now()
	t.state.Phase = "probing"
}

// refillTokens adds tokens to the bucket based on elapsed time.
func (t *SourceThrottler) refillTokens(now time.Time) {
	elapsed := now.Sub(t.lastRefill)
	tokensToAdd := elapsed.Seconds() * t.cfg.TokensPerSecond
	t.tokens = math.Min(float64(t.cfg.Burst), t.tokens+tokensToAdd)
	t.lastRefill = now
}

// Metrics returns a map of key throttle metrics for telemetry.
func (t *SourceThrottler) Metrics() map[string]interface{} {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return map[string]interface{}{
		"source":             t.cfg.Name,
		"phase":              t.state.Phase,
		"consecutive_errors": t.state.ConsecutiveErrors,
		"backoff_seconds":    t.state.Backoff.Seconds(),
		"retry_after":        t.state.RetryAfter.Seconds(),
		"tokens_available":   t.tokens,
		"total_requests":     t.state.TotalRequests,
		"total_throttled":    t.state.TotalThrottled,
		"last_status":        t.state.LastStatusCode,
	}
}

// parseRetryAfter parses the Retry-After header from an HTTP response.
// Supports both seconds (integer) and HTTP-date formats.
func parseRetryAfter(resp *http.Response) time.Duration {
	header := resp.Header.Get("Retry-After")
	if header == "" {
		header = resp.Header.Get("retry-after")
	}
	if header == "" {
		return 0
	}

	// Try seconds-first (integer)
	seconds, err := strconv.Atoi(strings.TrimSpace(header))
	if err == nil {
		return time.Duration(seconds) * time.Second
	}

	// Try HTTP-date format
	parsed, err := time.Parse(time.RFC1123, strings.TrimSpace(header))
	if err == nil {
		return time.Until(parsed)
	}

	return 0
}

// MultiSourceThrottler manages throttles for multiple sources.
// This is the top-level throttle coordinator.
type MultiSourceThrottler struct {
	mu       sync.RWMutex
	sources  map[string]*SourceThrottler
	defaultCfg ThrottlerConfig
}

// NewMultiSourceThrottler creates a throttle coordinator.
func NewMultiSourceThrottler() *MultiSourceThrottler {
	return &MultiSourceThrottler{
		sources:    make(map[string]*SourceThrottler),
		defaultCfg: DefaultThrottlerConfig("default"),
	}
}

// Register adds a source with its throttle config.
// If called after the source exists, updates config without resetting state.
func (m *MultiSourceThrottler) Register(name string, cfg ThrottlerConfig) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if existing, ok := m.sources[name]; ok {
		existing.cfg = cfg
		return
	}
	m.sources[name] = NewSourceThrottler(cfg)
}

// Get returns the throttler for a source, creating one with defaults if missing.
func (m *MultiSourceThrottler) Get(name string) *SourceThrottler {
	m.mu.Lock()
	defer m.mu.Unlock()

	if t, ok := m.sources[name]; ok {
		return t
	}
	cfg := DefaultThrottlerConfig(name)
	t := NewSourceThrottler(cfg)
	m.sources[name] = t
	return t
}

// AllStates returns throttle states for all sources.
func (m *MultiSourceThrottler) AllStates() map[string]ThrottleState {
	m.mu.RLock()
	defer m.mu.RUnlock()

	states := make(map[string]ThrottleState, len(m.sources))
	for name, t := range m.sources {
		states[name] = t.State()
	}
	return states
}

// AllMetrics returns metrics for all sources.
func (m *MultiSourceThrottler) AllMetrics() []map[string]interface{} {
	m.mu.RLock()
	defer m.mu.RUnlock()

	var metrics []map[string]interface{}
	for _, t := range m.sources {
		metrics = append(metrics, t.Metrics())
	}
	return metrics
}
