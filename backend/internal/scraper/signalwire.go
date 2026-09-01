package scraper

import (
	"context"
	"fmt"
	"time"

	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/safecall"
	"github.com/safering/backend/internal/store"
	"go.uber.org/zap"
)

// SignalWireSync fetches phone numbers owned by our SignalWire account(s)
// and adds them to the allowlist so they are never flagged as scams.
//
// Also cross-references against the scam DB to detect spoofed/compromised numbers.
//
// This is NOT a Scraper (doesn't produce scam entries) — it's a sync job.
type SignalWireSync struct {
	logger     *zap.Logger
	scStore    *store.ScamNumberStore
	allowStore *store.AllowlistStore
	swClient   *safecall.SWClient
}

// NewSignalWireSync creates the sync from env-configured SignalWire credentials.
func NewSignalWireSync(
	logger *zap.Logger,
	scStore *store.ScamNumberStore,
	allowStore *store.AllowlistStore,
) *SignalWireSync {
	return &SignalWireSync{
		logger:     logger.Named("sync.signalwire"),
		scStore:    scStore,
		allowStore: allowStore,
		swClient:   safecall.NewSWClientFromEnv(),
	}
}

// Enabled reports whether SignalWire credentials are configured.
func (s *SignalWireSync) Enabled() bool {
	return s.swClient.Enabled()
}

// SyncResult summarizes what happened in a sync run.
type SyncResult struct {
	Source       string        `json:"source"`
	NumbersSynced int          `json:"numbers_synced"`
	NewEntries   int           `json:"new_entries"`
	SpoofedFlags int           `json:"spoofed_flags"`
	Duration     time.Duration `json:"duration_ms"`
	Errors       []string      `json:"errors,omitempty"`
}

// Run fetches all numbers from the configured SignalWire account
// and adds them to the allowlist.
func (s *SignalWireSync) Run(ctx context.Context) (*SyncResult, error) {
	if !s.Enabled() {
		s.logger.Debug("signalwire not configured, skipping sync")
		return &SyncResult{Source: "signalwire"}, nil
	}

	start := time.Now()
	result := &SyncResult{Source: "signalwire"}

	numbers, err := s.swClient.ListPhoneNumbers(ctx)
	if err != nil {
		result.Errors = append(result.Errors, err.Error())
		result.Duration = time.Since(start)
		return result, fmt.Errorf("list phone numbers: %w", err)
	}

	result.NumbersSynced = len(numbers)
	s.logger.Info("signalwire numbers fetched", zap.Int("count", len(numbers)))

	var entries []*model.AllowlistEntry
	for _, num := range numbers {
		hash := HashPhoneNumber(num.PhoneNumber)
		entry := &model.AllowlistEntry{
			NumberHash: hash,
			E164:       num.PhoneNumber,
			Label:      fmt.Sprintf("SignalWire: %s", num.FriendlyName),
			Source:     model.AllowlistSourceSignalWire,
		}
		entries = append(entries, entry)

		// Cross-reference: check if this number is in our scam DB
		scamEntry, err := s.scStore.GetByHash(ctx, hash)
		if err == nil && scamEntry != nil {
			s.logger.Warn("⚠️ signalwire number found in scam DB — possible spoofing!",
				zap.String("number", num.PhoneNumber),
				zap.String("scam_type", scamEntry.ScamType),
				zap.Float64("risk_score", scamEntry.RiskScore))
			result.SpoofedFlags++
		}
	}

	newEntries, err := s.allowStore.BulkAdd(ctx, entries)
	if err != nil {
		result.Errors = append(result.Errors, err.Error())
		result.Duration = time.Since(start)
		return result, fmt.Errorf("bulk add allowlist: %w", err)
	}
	result.NewEntries = newEntries

	result.Duration = time.Since(start)
	s.logger.Info("signalwire sync complete",
		zap.Int("synced", result.NumbersSynced),
		zap.Int("new", result.NewEntries),
		zap.Int("spoofed_flags", result.SpoofedFlags),
		zap.Duration("duration", result.Duration))
	return result, nil
}
