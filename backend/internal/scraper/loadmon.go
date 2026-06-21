// Package scraper — load-aware opportunistic scraper scheduler.
//
// The LoadMonitor watches system resources and adjusts the scraper's
// appetite accordingly:
//
//	Idle    → full throttle (5s interval, 3 concurrent workers)
//	Normal  → steady state (15s interval, 1-2 workers)
//	Busy    → back off (60s interval, 1 worker, high-priority only)
//	Critical → pause all scraping
//
// The goal: your machines never notice us. We're like a crypto miner
// that only mines when GPU fans are silent.
package scraper

import (
	"context"
	"fmt"
	"math"
	"os"
	"runtime"
	"sync"
	"time"

	"go.uber.org/zap"
)

// SystemLoad captures a snapshot of host resource usage.
type SystemLoad struct {
	CPUPercent    float64 `json:"cpu_percent"`    // 0-100
	LoadAvg1      float64 `json:"load_avg_1"`     // 1-min load average
	LoadAvg5      float64 `json:"load_avg_5"`     // 5-min
	MemoryPercent float64 `json:"memory_percent"` // 0-100
	NumCPU        int     `json:"num_cpu"`        // logical CPUs
	// Cached values
	normalizedLoad float64 // 0.0 (idle) - 1.0 (saturated)
}

// LoadLevel represents the system's current resource availability.
type LoadLevel int

const (
	LoadIdle     LoadLevel = iota // Wide open, take everything
	LoadNormal                    // Some headroom, be reasonable
	LoadBusy                      // Tight, be minimal
	LoadCritical                  // System is under pressure, yield completely
)

// LoadMonitor watches system resources and feeds back guidance to the agent.
type LoadMonitor struct {
	mu           sync.RWMutex
	current      SystemLoad
	history      []SystemLoad // sliding window for trend detection
	historyMax   int
	logger       *zap.Logger
	stopCh       chan struct{}
	lastCPUIDLE  uint64 // cached /proc/stat idle value for delta
	lastCPUTotal uint64 // cached /proc/stat total value for delta
}

// LoadMonitorConfig provides resource monitor parameters.
type LoadMonitorConfig struct {
	// Sample interval for checking system resources
	SampleInterval time.Duration
	// Number of samples to keep for trend detection
	HistorySize int
}

// DefaultLoadMonitorConfig returns sensible defaults.
func DefaultLoadMonitorConfig() LoadMonitorConfig {
	return LoadMonitorConfig{
		SampleInterval: 5 * time.Second,
		HistorySize:    12, // 1 minute of samples at 5s interval
	}
}

// NewLoadMonitor creates a system resource monitor.
// Starts collecting data immediately. Call Start() to begin background sampling.
func NewLoadMonitor(logger *zap.Logger) *LoadMonitor {
	return NewLoadMonitorWithConfig(logger, DefaultLoadMonitorConfig())
}

// NewLoadMonitorWithConfig creates a resource monitor with custom config.
func NewLoadMonitorWithConfig(logger *zap.Logger, cfg LoadMonitorConfig) *LoadMonitor {
	return &LoadMonitor{
		history:    make([]SystemLoad, 0, cfg.HistorySize),
		historyMax: cfg.HistorySize,
		logger:     logger.Named("scraper.loadmon"),
		stopCh:     make(chan struct{}),
	}
}

// Start begins background system resource sampling.
// Blocks until ctx is cancelled or an error occurs.
func (m *LoadMonitor) Start(ctx context.Context, interval time.Duration) {
	m.logger.Info("load monitor started",
		zap.Duration("interval", interval),
		zap.Int("num_cpu", m.sampleNumCPU()),
	)

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	// Sample immediately
	m.sample()

	for {
		select {
		case <-ticker.C:
			m.sample()
		case <-ctx.Done():
			m.logger.Info("load monitor stopped")
			return
		}
	}
}

// Level returns the current system load level.
func (m *LoadMonitor) Level() LoadLevel {
	m.mu.RLock()
	defer m.mu.RUnlock()

	load := m.current.normalizedLoad
	switch {
	case load >= 0.8:
		return LoadCritical
	case load >= 0.5:
		return LoadBusy
	case load >= 0.2:
		return LoadNormal
	default:
		return LoadIdle
	}
}

// Load returns the current system load snapshot.
func (m *LoadMonitor) Load() SystemLoad {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.current
}

// Metrics returns key telemetry for the dashboard.
func (m *LoadMonitor) Metrics() map[string]interface{} {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return map[string]interface{}{
		"cpu_percent":    m.current.CPUPercent,
		"load_avg_1":     m.current.LoadAvg1,
		"memory_percent": m.current.MemoryPercent,
		"level":          m.Level().String(),
		"normalized":     m.current.normalizedLoad,
	}
}

// sample reads system resources and updates current state.
func (m *LoadMonitor) sample() {
	load := m.readSystemLoad()
	load.NumCPU = m.sampleNumCPU()
	load.normalizedLoad = m.computeNormalized(load)

	m.mu.Lock()
	m.current = load
	m.history = append(m.history, load)
	if len(m.history) > m.historyMax {
		m.history = m.history[1:]
	}
	m.mu.Unlock()
}

// readSystemLoad gathers the actual metrics from /proc and /sys.
func (m *LoadMonitor) readSystemLoad() SystemLoad {
	load := SystemLoad{}

	// CPU usage — read /proc/stat delta
	load.CPUPercent = m.readCPU()

	// Load average — read /proc/loadavg
	load.LoadAvg1, load.LoadAvg5 = m.readLoadAvg()

	// Memory — read /proc/meminfo
	load.MemoryPercent = m.readMemory()

	return load
}

// readCPU returns overall CPU usage percent (0-100).
// Uses /proc/stat with cached previous values for delta computation.
func (m *LoadMonitor) readCPU() float64 {
	data, err := os.ReadFile("/proc/stat")
	if err != nil {
		return 0
	}

	var user, nice, system, idle, iowait, irq, softirq, steal uint64
	n, _ := fmt.Sscanf(string(data), "cpu %d %d %d %d %d %d %d %d",
		&user, &nice, &system, &idle, &iowait, &irq, &softirq, &steal)
	if n < 4 {
		return 0
	}

	total := user + nice + system + idle + iowait + irq + softirq + steal
	idleTotal := idle + iowait

	// Compare with previous sample
	prevIDLE := m.lastCPUIDLE
	prevTOTAL := m.lastCPUTotal

	if prevTOTAL == 0 || prevIDLE == 0 {
		// First sample, cache and return 0
		m.lastCPUIDLE = idleTotal
		m.lastCPUTotal = total
		return 0
	}

	deltaIDLE := idleTotal - prevIDLE
	deltaTOTAL := total - prevTOTAL

	if deltaTOTAL == 0 {
		return 0
	}

	cpuPercent := (1.0 - float64(deltaIDLE)/float64(deltaTOTAL)) * 100.0

	m.lastCPUIDLE = idleTotal
	m.lastCPUTotal = total

	return math.Min(cpuPercent, 100.0)
}

// readLoadAvg reads the 1-minute and 5-minute load averages.
func (m *LoadMonitor) readLoadAvg() (float64, float64) {
	data, err := os.ReadFile("/proc/loadavg")
	if err != nil {
		return 0, 0
	}

	var load1, load5 float64
	n, _ := fmt.Sscanf(string(data), "%f %f", &load1, &load5)
	if n < 2 {
		return 0, 0
	}

	return load1, load5
}

// readMemory reads memory usage as a percentage (0-100).
func (m *LoadMonitor) readMemory() float64 {
	data, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return 0
	}

	var total, available uint64
	n, _ := fmt.Sscanf(string(data),
		"MemTotal: %d kB\nMemFree: %d kB\nMemAvailable: %d",
		&total, &available, &available)
	if n < 2 {
		// Try harder parsing /proc/meminfo
		_ = n
		_, _ = fmt.Sscanf(string(data), "MemTotal: %d kB", &total)
		_, _ = fmt.Sscanf(string(data[100:]), "MemAvailable: %d kB", &available)
	}

	if total == 0 {
		return 0
	}

	return (1.0 - float64(available)/float64(total)) * 100.0
}

// sampleNumCPU returns the number of logical CPUs.
func (m *LoadMonitor) sampleNumCPU() int {
	return runtime.NumCPU()
}

// computeNormalized blends metrics into a single 0-1 saturation value.
func (m *LoadMonitor) computeNormalized(load SystemLoad) float64 {
	numCPU := float64(load.NumCPU)
	if numCPU < 1 {
		numCPU = 1
	}

	// CPU: 0-100% → 0-1
	cpuNorm := load.CPUPercent / 100.0

	// Load average vs CPU count: load/CPU > 1 means overloaded
	loadNorm := load.LoadAvg1 / numCPU
	if loadNorm > 1.0 {
		loadNorm = 1.0 + (loadNorm-1.0)*0.5 // Soft-clip above 1
	}
	if loadNorm > 2.0 {
		loadNorm = 2.0
	}
	loadNorm = loadNorm / 2.0 // Normalize to 0-1

	// Memory: 0-100% → 0-1
	memNorm := load.MemoryPercent / 100.0

	// Weighted blend: CPU > load > memory
	// CPU matters most — if something is spinning, we should yield
	normalized := cpuNorm*0.5 + loadNorm*0.3 + memNorm*0.2

	return math.Min(normalized, 1.0)
}

// cached /proc/stat counters for delta computation
var (
	lastIdle  uint64
	lastTotal uint64
)

// String returns a human-readable load level.
func (l LoadLevel) String() string {
	switch l {
	case LoadIdle:
		return "idle"
	case LoadNormal:
		return "normal"
	case LoadBusy:
		return "busy"
	case LoadCritical:
		return "critical"
	default:
		return "unknown"
	}
}

// ScrapeParams returns recommended scrape parameters for a given load level.
// "You idle, we work you" — idle machines get max aggression.
func (l LoadLevel) ScrapeParams() (interval time.Duration, concurrency int, highPriorityOnly bool) {
	switch l {
	case LoadIdle:
		// Machine is quiet — full throttle
		return 5 * time.Second, 3, false
	case LoadNormal:
		// Some headroom — steady pace
		return 15 * time.Second, 2, false
	case LoadBusy:
		// Machine is working — be polite
		return 60 * time.Second, 1, true
	case LoadCritical:
		// Machine is struggling — vanish
		return 5 * time.Minute, 0, true
	default:
		return 30 * time.Second, 1, false
	}
}
