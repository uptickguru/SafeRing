package scraper

import (
	"context"
	"fmt"
	"time"
)

// SourcePriority defines how aggressively a source should be scraped.
type SourcePriority int

const (
	PriorityCritical SourcePriority = iota
	PriorityHigh
	PriorityNormal
	PriorityLow
	PriorityOpportunistic // Only scrape when idle
)

// SourceDescriptor describes a data source and its scraping strategy.
// Each source can have multiple endpoint strategies for resilience.
type SourceDescriptor struct {
	// Unique name for this source
	Name string
	// Human-readable description
	Description string
	// Priority affects scrape frequency
	Priority SourcePriority
	// Credibility weight for entries from this source (0.0 - 1.0)
	Credibility float64
	// Whether this source needs authentication
	RequiresAuth bool
	// Endpoint strategies in preference order
	Strategies []ScrapeStrategy
	// Minimum interval between scrape attempts
	MinInterval time.Duration
	// Maximum records to collect per scrape run
	MaxRecords int
	// Whether this source is currently enabled
	Enabled bool
	// DefaultUseProxy routes all strategies through SOCKS5 when true.
	// Individual strategies can override.
	DefaultUseProxy bool
}

// ScrapeStrategy defines one way to scrape a source.
// If one endpoint/strategy fails, we try the next.
type ScrapeStrategy struct {
	// Type: "api", "html", "rss", "sitemap", "csv", "json"
	Type string
	// URL to fetch
	URL string
	// Expected content type
	ContentType string
	// Additional HTTP headers
	Headers map[string]string
	// Whether HTML parsing is needed
	ParseHTML bool
	// CSS selector or JSONPath for extracting entries
	Selector string
	// Fallback strategy if this one fails
	FallbackURL string
	// UseProxy routes this strategy through the agent's configured SOCKS5 proxy.
	// True for sources that rate-limit by IP or geo-block.
	UseProxy bool
}

// SourceRegistry maintains the list of all available data sources
// and their scraping strategies. This is the "intel portfolio" —
// what we mine, where we mine it, and how.
type SourceRegistry struct {
	sources []SourceDescriptor
}

// NewDefaultRegistry builds the default set of sources with working strategies.
func NewDefaultRegistry() *SourceRegistry {
	return &SourceRegistry{
		sources: defaultSources(),
	}
}

// defaultSources returns the built-in source definitions.
// These are real, working endpoints discovered through testing.
func defaultSources() []SourceDescriptor {
	return []SourceDescriptor{
		{
			Name:          "ftc-gov",
			Description:   "FTC Consumer Sentinel Network (government fraud reports)",
			Priority:      PriorityHigh,
			Credibility:   0.65,
			RequiresAuth:  false,
			MinInterval:   6 * time.Hour,
			MaxRecords:    1000,
			Enabled:       true,
			Strategies: []ScrapeStrategy{
				{
					Type:    "html",
					URL:     "https://www.ftc.gov/enforcement/consumer-sentinel-network",
					ParseHTML: true,
					Selector: ".c-view__rows article",
					FallbackURL: "https://www.ftc.gov/news-events/data-visualizations/consumer-sentinel-network-data-book",
				},
				{
					Type:    "html",
					URL:     "https://www.ftc.gov/news-events/data-visualizations/consumer-sentinel-network-data-book",
					ParseHTML: true,
					Selector: ".data-table tbody tr",
				},
			},
		},
		{
			Name:          "bbb-scamtracker",
			Description:   "BBB Scam Tracker (consumer-reported scams)",
			Priority:      PriorityHigh,
			Credibility:   0.7,
			RequiresAuth:  false,
			MinInterval:   6 * time.Hour,
			MaxRecords:    500,
			Enabled:       true,
			Strategies: []ScrapeStrategy{
				{
					Type:    "html",
					URL:     "https://www.bbb.org/scamtracker/lookupscam",
					ParseHTML: true,
					Selector: ".scam-report .phone-number",
				},
				{
					Type:    "html",
					URL:     "https://www.bbb.org/scamtracker",
					ParseHTML: true,
					Selector: "[data-testid='scam-report']",
				},
			},
		},
		{
			Name:            "reddit-r-scams",
			Description:     "Reddit r/scams (community-reported scam numbers)",
			Priority:        PriorityNormal,
			Credibility:     0.45,
			RequiresAuth:    false,
			MinInterval:     1 * time.Hour,
			MaxRecords:      200,
			Enabled:         true,
			DefaultUseProxy: true,
			Strategies: []ScrapeStrategy{
				{
					Type:     "json",
					URL:      "https://www.reddit.com/r/scams.json?limit=100&t=day",
					UseProxy: true,
					Headers: map[string]string{
						"User-Agent":           "SafeRing/1.0 (scam detection research; +https://safering.app)",
						"Accept":               "application/json",
					},
					FallbackURL: "https://old.reddit.com/r/scams.json?limit=100",
				},
				{
					Type:     "json",
					URL:      "https://old.reddit.com/r/scams.json?limit=100&t=day",
					UseProxy: true,
					Headers: map[string]string{
						"User-Agent": "SafeRing/1.0 (scam detection research)",
						"Accept":     "application/json",
					},
				},
			},
		},
		{
			Name:            "reddit-r-scamnumbers",
			Description:     "Reddit r/scamnumbers (dedicated scam number reports)",
			Priority:        PriorityNormal,
			Credibility:     0.5,
			RequiresAuth:    false,
			MinInterval:     1 * time.Hour,
			MaxRecords:      100,
			Enabled:         true,
			DefaultUseProxy: true,
			Strategies: []ScrapeStrategy{
				{
					Type:     "json",
					URL:      "https://www.reddit.com/r/scamnumbers.json?limit=100&t=day",
					UseProxy: true,
					Headers: map[string]string{
						"User-Agent": "SafeRing/1.0 (scam detection research)",
						"Accept":     "application/json",
					},
					FallbackURL: "https://old.reddit.com/r/scamnumbers.json?limit=100",
				},
				{
					Type:     "json",
					URL:      "https://old.reddit.com/r/scamnumbers.json?limit=100&t=day",
					UseProxy: true,
					Headers: map[string]string{
						"User-Agent": "SafeRing/1.0 (scam detection research)",
						"Accept":     "application/json",
					},
				},
			},
		},
		{
			Name:            "reddit-r-phonescam",
			Description:     "Reddit r/phoneScam (phone scam reports)",
			Priority:        PriorityLow,
			Credibility:     0.45,
			RequiresAuth:    false,
			MinInterval:     2 * time.Hour,
			MaxRecords:      100,
			Enabled:         true,
			DefaultUseProxy: true,
			Strategies: []ScrapeStrategy{
				{
					Type:     "json",
					URL:      "https://www.reddit.com/r/phoneScam.json?limit=100&t=day",
					UseProxy: true,
					Headers: map[string]string{
						"User-Agent": "SafeRing/1.0 (scam detection research)",
						"Accept":     "application/json",
					},
					FallbackURL: "https://old.reddit.com/r/phoneScam.json?limit=100",
				},
				{
					Type:     "json",
					URL:      "https://old.reddit.com/r/phoneScam.json?limit=100&t=day",
					UseProxy: true,
					Headers: map[string]string{
						"User-Agent": "SafeRing/1.0 (scam detection research)",
						"Accept":     "application/json",
					},
				},
			},
		},
		{
			Name:          "tellows",
			Description:   "Tellows (user-rated spam/telemarketing numbers)",
			Priority:      PriorityNormal,
			Credibility:   0.55,
			RequiresAuth:  false,
			MinInterval:   12 * time.Hour,
			MaxRecords:    500,
			Enabled:       false, // Disabled by default, too aggressive
			Strategies: []ScrapeStrategy{
				{
					Type:    "html",
					URL:     "https://www.tellows.de/num/blocklist",
					ParseHTML: true,
					Selector: ".numwrapper",
				},
			},
		},
		{
			Name:          "scamcallfighters",
			Description:   "ScamCallFighters (community scam call database)",
			Priority:      PriorityNormal,
			Credibility:   0.5,
			RequiresAuth:  false,
			MinInterval:   12 * time.Hour,
			MaxRecords:    300,
			Enabled:       false,
			Strategies: []ScrapeStrategy{
				{
					Type:    "html",
					URL:     "https://scamcallfighters.com",
					ParseHTML: true,
					Selector: ".phone-entry",
				},
			},
		},
		{
			Name:            "x-twitter",
			Description:     "X/Twitter search for scam phone numbers",
			Priority:        PriorityNormal,
			Credibility:     0.5,
			RequiresAuth:    true, // Twitter API OAuth 2.0
			MinInterval:     6 * time.Hour,
			MaxRecords:      300,
			Enabled:         false, // Requires API credentials
			DefaultUseProxy: false,
			Strategies: []ScrapeStrategy{
				{
					Type: "api",
					URL:  "https://api.twitter.com/2/tweets/search/recent?query=(scam%20OR%20scammer)%20(phone%20OR%20number%20OR%20call)%20-language:en&max_results=100&tweet.fields=created_at",
					Headers: map[string]string{
						"Authorization": "Bearer $TWITTER_BEARER_TOKEN",
					},
				},
			},
		},
	}
}

// Sources returns all registered sources.
func (r *SourceRegistry) Sources() []SourceDescriptor {
	return r.sources
}

// EnabledSources returns only enabled sources.
func (r *SourceRegistry) EnabledSources() []SourceDescriptor {
	var enabled []SourceDescriptor
	for _, s := range r.sources {
		if s.Enabled {
			enabled = append(enabled, s)
		}
	}
	return enabled
}

// Get returns a source by name.
func (r *SourceRegistry) Get(name string) (SourceDescriptor, bool) {
	for _, s := range r.sources {
		if s.Name == name {
			return s, true
		}
	}
	return SourceDescriptor{}, false
}

// Enable enables or disables a source by name.
func (r *SourceRegistry) Enable(name string, enabled bool) error {
	for i := range r.sources {
		if r.sources[i].Name == name {
			r.sources[i].Enabled = enabled
			return nil
		}
	}
	return fmt.Errorf("source not found: %s", name)
}

// SourceReporter defines the interface for telemetry and coordination.
// Agents report their findings and throttle state through this.
type SourceReporter interface {
	// ReportEntries submits scraped entries to the coordinator.
	ReportEntries(ctx context.Context, source string, entries []ScrapedEntry) error
	// ReportThrottle submits throttle telemetry (rate limits hit, backoff, etc.)
	ReportThrottle(ctx context.Context, source string, state ThrottleState) error
	// RequestTask asks the coordinator for a scrape task.
	RequestTask(ctx context.Context) (*ScrapeTask, error)
}

// ScrapeTask is a unit of work assigned to a worker agent.
type ScrapeTask struct {
	Source      string    `json:"source"`
	StrategyIdx int       `json:"strategy_idx"`
	Priority    int       `json:"priority"`
	AssignedAt  time.Time `json:"assigned_at"`
	Deadline    time.Time `json:"deadline"`
	AgentID     string    `json:"agent_id"`
	TaskID      string    `json:"task_id"`
}
