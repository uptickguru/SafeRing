package store

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/safering/backend/internal/model"
)

// ScamPrefixStore handles CRUD operations for the scam_prefixes table.
type ScamPrefixStore struct {
	db  *DB
	now func() time.Time
}

// NewScamPrefixStore creates a new ScamPrefixStore.
func NewScamPrefixStore(db *DB) *ScamPrefixStore {
	return &ScamPrefixStore{db: db, now: time.Now}
}

// GetByPrefix looks up a scam prefix by its prefix string.
func (s *ScamPrefixStore) GetByPrefix(ctx context.Context, prefix string) (*model.ScamPrefix, error) {
	query := `
		SELECT id, prefix, country_code, risk_score, scam_type,
		       report_count, samples_hashed, last_updated
		FROM scam_prefixes
		WHERE prefix = ?`

	var sp model.ScamPrefix
	err := s.db.QueryRowContext(ctx, query, prefix).Scan(
		&sp.ID, &sp.Prefix, &sp.CountryCode, &sp.RiskScore, &sp.ScamType,
		&sp.ReportCount, &sp.SamplesHashed, &sp.LastUpdated,
	)
	if err == sql.ErrNoRows {
		return nil, model.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("query scam prefix: %w", err)
	}
	return &sp, nil
}

// Upsert inserts or updates a scam prefix record.
func (s *ScamPrefixStore) Upsert(ctx context.Context, sp *model.ScamPrefix) error {
	sp.LastUpdated = s.now()
	query := `
		INSERT INTO scam_prefixes (prefix, country_code, risk_score, scam_type,
		                           report_count, samples_hashed, last_updated)
		VALUES (?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(prefix) DO UPDATE SET
			risk_score     = excluded.risk_score,
			scam_type      = COALESCE(NULLIF(excluded.scam_type, ''), scam_prefixes.scam_type),
			report_count   = scam_prefixes.report_count + excluded.report_count,
			samples_hashed = CASE WHEN excluded.samples_hashed > 0 THEN excluded.samples_hashed ELSE scam_prefixes.samples_hashed END,
			last_updated   = excluded.last_updated`

	_, err := s.db.ExecContext(ctx, query,
		sp.Prefix, sp.CountryCode, sp.RiskScore, sp.ScamType,
		sp.ReportCount, sp.SamplesHashed, sp.LastUpdated,
	)
	if err != nil {
		return fmt.Errorf("upsert scam prefix: %w", err)
	}
	return nil
}

// GetAll returns all scam prefixes, ordered by risk score descending.
// This is the primary data source for the GET /v1/prefixes endpoint.
func (s *ScamPrefixStore) GetAll(ctx context.Context) ([]*model.ScamPrefix, error) {
	query := `
		SELECT id, prefix, country_code, risk_score, scam_type,
		       report_count, samples_hashed, last_updated
		FROM scam_prefixes
		WHERE risk_score > 0
		ORDER BY risk_score DESC, report_count DESC`

	rows, err := s.db.QueryContext(ctx, query)
	if err != nil {
		return nil, fmt.Errorf("query all prefixes: %w", err)
	}
	defer rows.Close()

	var results []*model.ScamPrefix
	for rows.Next() {
		var sp model.ScamPrefix
		if err := rows.Scan(&sp.ID, &sp.Prefix, &sp.CountryCode, &sp.RiskScore, &sp.ScamType,
			&sp.ReportCount, &sp.SamplesHashed, &sp.LastUpdated); err != nil {
			return nil, fmt.Errorf("scan prefix row: %w", err)
		}
		results = append(results, &sp)
	}
	return results, rows.Err()
}

// GetHighRisk returns prefixes above the given risk threshold.
func (s *ScamPrefixStore) GetHighRisk(ctx context.Context, threshold float64) ([]*model.ScamPrefix, error) {
	query := `
		SELECT id, prefix, country_code, risk_score, scam_type,
		       report_count, samples_hashed, last_updated
		FROM scam_prefixes
		WHERE risk_score >= ?
		ORDER BY risk_score DESC
		LIMIT 100`

	rows, err := s.db.QueryContext(ctx, query, threshold)
	if err != nil {
		return nil, fmt.Errorf("query high risk prefixes: %w", err)
	}
	defer rows.Close()

	var results []*model.ScamPrefix
	for rows.Next() {
		var sp model.ScamPrefix
		if err := rows.Scan(&sp.ID, &sp.Prefix, &sp.CountryCode, &sp.RiskScore, &sp.ScamType,
			&sp.ReportCount, &sp.SamplesHashed, &sp.LastUpdated); err != nil {
			return nil, fmt.Errorf("scan high risk prefix row: %w", err)
		}
		results = append(results, &sp)
	}
	return results, rows.Err()
}

// GetCount returns the total number of scam prefixes.
func (s *ScamPrefixStore) GetCount(ctx context.Context) (int, error) {
	var count int
	err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM scam_prefixes").Scan(&count)
	return count, err
}
