package scraper

import (
	"context"
	"fmt"
	"sort"
	"sync"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// Aggregator manages multiple scrapers, runs them on a schedule,
// and persists results to the database with deduplication.
type Aggregator struct {
	scrapers    []Scraper
	numberStore *store.ScamNumberStore
	prefixStore *store.ScamPrefixStore
	logger      *zap.Logger
	interval    time.Duration
	mu          sync.RWMutex
	lastRun     time.Time
	stats       AggregatorStats
}

// AggregatorStats tracks aggregate metrics across scraper runs.
type AggregatorStats struct {
	TotalEntries    int              `json:"total_entries"`
	TotalNewEntries int              `json:"total_new_entries"`
	LastRunDuration time.Duration    `json:"last_run_duration"`
	LastRunTime     time.Time        `json:"last_run_time"`
	Sources         map[string]int   `json:"sources"`
	LastErrors      []string         `json:"last_errors,omitempty"`
}

// NewAggregator creates a new scraper aggregator.
func NewAggregator(
	numberStore *store.ScamNumberStore,
	prefixStore *store.ScamPrefixStore,
	interval time.Duration,
	logger *zap.Logger,
) *Aggregator {
	return &Aggregator{
		numberStore: numberStore,
		prefixStore: prefixStore,
		logger:      logger.Named("scraper.aggregator"),
		interval:    interval,
		stats: AggregatorStats{
			Sources: make(map[string]int),
		},
	}
}

// Register adds a scraper to the aggregator.
func (a *Aggregator) Register(scraper Scraper) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.scrapers = append(a.scrapers, scraper)
	a.logger.Info("registered scraper", zap.String("name", scraper.Name()))
}

// RunAll executes all registered scrapers and aggregates results.
func (a *Aggregator) RunAll(ctx context.Context) error {
	a.logger.Info("running all scrapers",
		zap.Int("count", len(a.scrapers)),
	)

	start := time.Now()
	var wg sync.WaitGroup
	errCh := make(chan error, len(a.scrapers))
	entryCh := make(chan []ScrapedEntry, len(a.scrapers))

	for _, s := range a.scrapers {
		wg.Add(1)
		go func(scraper Scraper) {
			defer wg.Done()
			entries, err := scraper.Scrape(ctx)
			if err != nil {
				errCh <- fmt.Errorf("%s: %w", scraper.Name(), err)
				return
			}
			entryCh <- entries
		}(s)
	}

	wg.Wait()
	close(errCh)
	close(entryCh)

	// Collect errors (non-fatal per scraper)
	var errors []string
	for err := range errCh {
		errors = append(errors, err.Error())
		a.logger.Error("scraper error", zap.Error(err))
	}

	// Aggregate all entries
	var allEntries []ScrapedEntry
	for entries := range entryCh {
		allEntries = append(allEntries, entries...)
	}

	// Deduplicate and persist
	newEntries, err := a.processEntries(ctx, allEntries)
	if err != nil {
		return fmt.Errorf("process entries: %w", err)
	}

	duration := time.Since(start)

	// Update stats
	a.mu.Lock()
	a.stats.TotalEntries = len(allEntries)
	a.stats.TotalNewEntries = newEntries
	a.stats.LastRunDuration = duration
	a.stats.LastRunTime = time.Now()
	a.stats.LastErrors = errors
	for _, e := range allEntries {
		a.stats.Sources[e.Source]++
	}
	a.mu.Unlock()

	a.logger.Info("scraper run complete",
		zap.Int("total_entries", len(allEntries)),
		zap.Int("new_entries", newEntries),
		zap.Duration("duration", duration),
		zap.Int("errors", len(errors)),
	)

	a.lastRun = time.Now()
	return nil
}

// processEntries deduplicates and persists scraped entries to the database.
// Returns the count of newly inserted entries.
func (a *Aggregator) processEntries(ctx context.Context, entries []ScrapedEntry) (int, error) {
	if len(entries) == 0 {
		return 0, nil
	}

	// Deduplicate by hash
	seen := make(map[string]*ScrapedEntry)
	for i := range entries {
		e := &entries[i]
		if existing, ok := seen[e.NumberHash]; ok {
			// Keep the one with higher risk score, preferring trusted sources
			if e.Source == "user_report" || e.RiskScore > existing.RiskScore {
				existing.RiskScore = e.RiskScore
				existing.ScamType = e.ScamType
				if e.ReportedAt.After(existing.ReportedAt) {
					existing.ReportedAt = e.ReportedAt
				}
			}
		} else {
			seen[e.NumberHash] = e
		}
	}

	// Build deduplicated list
	deduplicated := make([]*ScrapedEntry, 0, len(seen))
	for _, e := range seen {
		deduplicated = append(deduplicated, e)
	}

	// Sort by risk score descending
	sort.Slice(deduplicated, func(i, j int) bool {
		return deduplicated[i].RiskScore > deduplicated[j].RiskScore
	})

	// Process entries
	var newEntries int
	for _, entry := range deduplicated {
		existing, err := a.numberStore.GetByHash(ctx, entry.NumberHash)
		if err != nil {
			// New entry
			sn := entry.toScamNumber()
			if err := a.numberStore.Upsert(ctx, sn); err != nil {
				a.logger.Error("failed to insert entry", zap.Error(err))
				continue
			}
			newEntries++
		} else {
			// Update existing
			sn := entry.toScamNumber()
			sn.ReportCount = existing.ReportCount + 1
			if entry.RiskScore > existing.RiskScore {
				sn.RiskScore = entry.RiskScore
			}
			if err := a.numberStore.Upsert(ctx, sn); err != nil {
				a.logger.Error("failed to update entry", zap.Error(err))
				continue
			}
		}

		// Update prefix aggregation
		prefixHash := entry.prefixHash()
		if prefixHash != "" {
			a.updatePrefix(ctx, prefixHash, entry)
		}
	}

	return newEntries, nil
}

// updatePrefix creates or updates the aggregated prefix risk for a given prefix.
func (a *Aggregator) updatePrefix(ctx context.Context, prefix string, entry *ScrapedEntry) {
	existing, err := a.prefixStore.GetByPrefix(ctx, prefix)
	if err != nil {
		// Create new prefix entry
		sp := &model.ScamPrefix{
			Prefix:        prefix,
			CountryCode:   "1",
			RiskScore:     entry.RiskScore * 0.7, // Lower confidence for prefixes
			ScamType:      entry.ScamType,
			ReportCount:   1,
			SamplesHashed: 1,
		}
		if err := a.prefixStore.Upsert(ctx, sp); err != nil {
			a.logger.Error("failed to create prefix", zap.Error(err))
		}
	} else {
		// Update existing prefix
		existing.ReportCount++
		existing.SamplesHashed++
		// Weighted average for risk score
		existing.RiskScore = (existing.RiskScore*float64(existing.SamplesHashed-1) + entry.RiskScore) / float64(existing.SamplesHashed)
		if existing.RiskScore > 1.0 {
			existing.RiskScore = 1.0
		}
		if err := a.prefixStore.Upsert(ctx, existing); err != nil {
			a.logger.Error("failed to update prefix", zap.Error(err))
		}
	}
}

// GetStats returns a copy of the current aggregator statistics.
func (a *Aggregator) Stats() AggregatorStats {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.stats
}

// LastRun returns the time of the last completed scrape run.
func (a *Aggregator) LastRun() time.Time {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.lastRun
}

// toScamNumber converts a ScrapedEntry to a model.ScamNumber.
func (e *ScrapedEntry) toScamNumber() *model.ScamNumber {
	expiresAt := time.Now().Add(90 * 24 * time.Hour) // 90-day expiry window
	return &model.ScamNumber{
		NumberHash: e.NumberHash,
		Source:     e.Source,
		ScamType:   e.ScamType,
		RiskScore:  e.RiskScore,
		FirstSeen:  e.ReportedAt,
		ExpiresAt:  &expiresAt,
	}
}

// prefixHash extracts a prefix identifier from the hash.
func (e *ScrapedEntry) prefixHash() string {
	if len(e.NumberHash) < 10 {
		return ""
	}
	return e.NumberHash[:10]
}

// StartBackground runs the scraper loop on a ticker in the background.
// It blocks until the context is cancelled.
func (a *Aggregator) StartBackground(ctx context.Context) {
	a.logger.Info("starting background scraper",
		zap.Duration("interval", a.interval),
		zap.Int("scrapers", len(a.scrapers)),
	)

	// Run immediately on start
	if err := a.RunAll(ctx); err != nil {
		a.logger.Error("initial scrape failed", zap.Error(err))
	}

	ticker := time.NewTicker(a.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			a.logger.Info("scheduled scrape starting")
			if err := a.RunAll(ctx); err != nil {
				a.logger.Error("scheduled scrape failed", zap.Error(err))
			}
		case <-ctx.Done():
			a.logger.Info("background scraper stopped")
			return
		}
	}
}
