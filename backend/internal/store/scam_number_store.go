package store

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/safering/backend/internal/model"
)

// ScamNumberStore handles CRUD operations for the scam_numbers table.
type ScamNumberStore struct {
	db  *DB
	now func() time.Time
}

// NewScamNumberStore creates a new ScamNumberStore.
func NewScamNumberStore(db *DB) *ScamNumberStore {
	return &ScamNumberStore{db: db, now: time.Now}
}

// GetByHash looks up a scam number by its SHA-256 hash.
// Returns model.ErrNotFound if the hash is not in the database.
func (s *ScamNumberStore) GetByHash(ctx context.Context, hash string) (*model.ScamNumber, error) {
	query := `
		SELECT id, number_hash, source, scam_type, risk_score,
		       report_count, first_seen, last_updated, expires_at
		FROM scam_numbers
		WHERE number_hash = ?`

	var sn model.ScamNumber
	err := s.db.QueryRowContext(ctx, query, hash).Scan(
		&sn.ID, &sn.NumberHash, &sn.Source, &sn.ScamType, &sn.RiskScore,
		&sn.ReportCount, &sn.FirstSeen, &sn.LastUpdated, &sn.ExpiresAt,
	)
	if err == sql.ErrNoRows {
		return nil, model.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("query scam number: %w", err)
	}
	return &sn, nil
}

// Upsert inserts or updates a scam number record.
// Uses a unique constraint on number_hash for conflict resolution.
func (s *ScamNumberStore) Upsert(ctx context.Context, sn *model.ScamNumber) error {
	now := s.now()
	sn.LastUpdated = now

	query := `
		INSERT INTO scam_numbers (number_hash, source, scam_type, risk_score,
		                          report_count, first_seen, last_updated, expires_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(number_hash) DO UPDATE SET
			risk_score   = excluded.risk_score,
			source       = CASE WHEN scam_numbers.source = 'unknown' THEN excluded.source ELSE scam_numbers.source END,
			scam_type    = COALESCE(NULLIF(excluded.scam_type, ''), scam_numbers.scam_type),
			report_count = scam_numbers.report_count + excluded.report_count,
			last_updated = excluded.last_updated,
			expires_at   = excluded.expires_at`

	_, err := s.db.ExecContext(ctx, query,
		sn.NumberHash, sn.Source, sn.ScamType, sn.RiskScore,
		sn.ReportCount, now, now, sn.ExpiresAt,
	)
	if err != nil {
		return fmt.Errorf("upsert scam number: %w", err)
	}
	return nil
}

// BulkInsert performs a batch insert of scam numbers within a transaction.
// Skips duplicates silently (ON CONFLICT DO NOTHING for initial loads).
func (s *ScamNumberStore) BulkInsert(ctx context.Context, numbers []*model.ScamNumber) (int, error) {
	if len(numbers) == 0 {
		return 0, nil
	}

	var inserted int
	err := s.db.WithTx(ctx, func(tx *sql.Tx) error {
		stmt, err := tx.PrepareContext(ctx, `
			INSERT OR IGNORE INTO scam_numbers
			    (number_hash, source, scam_type, risk_score, report_count, first_seen, last_updated, expires_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
		if err != nil {
			return fmt.Errorf("prepare bulk insert: %w", err)
		}
		defer stmt.Close()

		now := s.now()
		for _, sn := range numbers {
			if sn.NumberHash == "" {
				continue
			}
			result, err := stmt.ExecContext(ctx,
				sn.NumberHash, sn.Source, sn.ScamType, sn.RiskScore,
				sn.ReportCount, now, now, sn.ExpiresAt,
			)
			if err != nil {
				return fmt.Errorf("bulk insert row: %w", err)
			}
			n, _ := result.RowsAffected()
			inserted += int(n)
		}
		return nil
	})
	return inserted, err
}

// IncrementReportCount atomically increments the report count for a hash.
func (s *ScamNumberStore) IncrementReportCount(ctx context.Context, hash string) error {
	_, err := s.db.ExecContext(ctx, `
		UPDATE scam_numbers
		SET report_count = report_count + 1, last_updated = ?
		WHERE number_hash = ?`,
		s.now(), hash,
	)
	if err != nil {
		return fmt.Errorf("increment report count: %w", err)
	}
	return nil
}

// GetHighRisk returns numbers with a risk score above the given threshold.
// Limit caps the number of results (max 1000).
func (s *ScamNumberStore) GetHighRisk(ctx context.Context, threshold float64, limit int) ([]*model.ScamNumber, error) {
	if limit <= 0 || limit > 1000 {
		limit = 100
	}

	query := `
		SELECT id, number_hash, source, scam_type, risk_score,
		       report_count, first_seen, last_updated, expires_at
		FROM scam_numbers
		WHERE risk_score >= ?
		ORDER BY risk_score DESC, report_count DESC
		LIMIT ?`

	rows, err := s.db.QueryContext(ctx, query, threshold, limit)
	if err != nil {
		return nil, fmt.Errorf("query high risk: %w", err)
	}
	defer rows.Close()

	var results []*model.ScamNumber
	for rows.Next() {
		var sn model.ScamNumber
		if err := rows.Scan(&sn.ID, &sn.NumberHash, &sn.Source, &sn.ScamType, &sn.RiskScore,
			&sn.ReportCount, &sn.FirstSeen, &sn.LastUpdated, &sn.ExpiresAt); err != nil {
			return nil, fmt.Errorf("scan high risk row: %w", err)
		}
		results = append(results, &sn)
	}
	return results, rows.Err()
}

// GetCount returns the total number of scam numbers in the database.
func (s *ScamNumberStore) GetCount(ctx context.Context) (int, error) {
	var count int
	err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM scam_numbers").Scan(&count)
	return count, err
}

// DeleteExpired removes entries whose expires_at is in the past.
func (s *ScamNumberStore) DeleteExpired(ctx context.Context) (int, error) {
	result, err := s.db.ExecContext(ctx, "DELETE FROM scam_numbers WHERE expires_at IS NOT NULL AND expires_at < ?", s.now())
	if err != nil {
		return 0, fmt.Errorf("delete expired: %w", err)
	}
	n, _ := result.RowsAffected()
	return int(n), nil
}
