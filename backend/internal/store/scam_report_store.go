package store

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/safering/backend/internal/model"
)

// ScamReportStore handles CRUD operations for the scam_reports table.
type ScamReportStore struct {
	db  *DB
	now func() time.Time
}

// NewScamReportStore creates a new ScamReportStore.
func NewScamReportStore(db *DB) *ScamReportStore {
	return &ScamReportStore{db: db, now: time.Now}
}

// Insert creates a new scam report and updates the associated scam_number record.
// This is the primary write path for the POST /v1/report endpoint.
func (s *ScamReportStore) Insert(ctx context.Context, report *model.ScamReport) error {
	now := s.now()
	if report.ReportedAt.IsZero() {
		report.ReportedAt = now
	}
	report.Source = "user_report"

	return s.db.WithTx(ctx, func(tx *sql.Tx) error {
		// Insert the report
		_, err := tx.ExecContext(ctx, `
			INSERT INTO scam_reports (number_hash, tag, reported_at, source)
			VALUES (?, ?, ?, ?)`,
			report.NumberHash, report.Tag, report.ReportedAt, report.Source,
		)
		if err != nil {
			return fmt.Errorf("insert scam report: %w", err)
		}

		// Upsert the associated scam number record
		_, err = tx.ExecContext(ctx, `
			INSERT INTO scam_numbers (number_hash, source, scam_type, risk_score, report_count, first_seen, last_updated)
			VALUES (?, 'user_report', ?, 0.6, 1, ?, ?)
			ON CONFLICT(number_hash) DO UPDATE SET
				risk_score   = MIN(1.0, scam_numbers.risk_score + 0.1),
				report_count = scam_numbers.report_count + 1,
				scam_type    = CASE
					WHEN ? != '' AND ? IS NOT NULL THEN ?
					ELSE scam_numbers.scam_type
				END,
				last_updated = ?`,
			report.NumberHash, report.Tag, now, now,
			report.Tag, report.Tag, report.Tag,
			now,
		)
		if err != nil {
			return fmt.Errorf("upsert number from report: %w", err)
		}

		// Extract prefix and upsert it too
		prefix := extractPrefix(report.NumberHash)
		if prefix != "" {
			_, err = tx.ExecContext(ctx, `
				INSERT INTO scam_prefixes (prefix, country_code, risk_score, report_count, samples_hashed, last_updated)
				VALUES (?, '1', 0.3, 1, 1, ?)
				ON CONFLICT(prefix) DO UPDATE SET
					risk_score     = MIN(1.0, scam_prefixes.risk_score + 0.05),
					report_count   = scam_prefixes.report_count + 1,
					samples_hashed = scam_prefixes.samples_hashed + 1,
					last_updated   = ?`,
				prefix, now, now,
			)
			if err != nil {
				return fmt.Errorf("upsert prefix from report: %w", err)
			}
		}

		return nil
	})
}

// GetRecent returns the most recent scam reports, ordered by reported_at descending.
func (s *ScamReportStore) GetRecent(ctx context.Context, limit int) ([]*model.ScamReport, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}

	rows, err := s.db.QueryContext(ctx, `
		SELECT id, number_hash, tag, reported_at, source
		FROM scam_reports
		ORDER BY reported_at DESC
		LIMIT ?`, limit)
	if err != nil {
		return nil, fmt.Errorf("query recent reports: %w", err)
	}
	defer rows.Close()

	var results []*model.ScamReport
	for rows.Next() {
		var r model.ScamReport
		if err := rows.Scan(&r.ID, &r.NumberHash, &r.Tag, &r.ReportedAt, &r.Source); err != nil {
			return nil, fmt.Errorf("scan report row: %w", err)
		}
		results = append(results, &r)
	}
	return results, rows.Err()
}

// GetCountByTag returns the count of reports grouped by tag.
// Used for the GET /v1/stats "top_scams" response.
func (s *ScamReportStore) GetCountByTag(ctx context.Context) (map[string]int, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT COALESCE(tag, 'untagged') as tag, COUNT(*) as count
		FROM scam_reports
		GROUP BY tag
		ORDER BY count DESC`)
	if err != nil {
		return nil, fmt.Errorf("query report counts by tag: %w", err)
	}
	defer rows.Close()

	results := make(map[string]int)
	for rows.Next() {
		var tag string
		var count int
		if err := rows.Scan(&tag, &count); err != nil {
			return nil, fmt.Errorf("scan tag count row: %w", err)
		}
		results[tag] = count
	}
	return results, rows.Err()
}

// GetTotalCount returns the total number of reports.
func (s *ScamReportStore) GetTotalCount(ctx context.Context) (int, error) {
	var count int
	err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM scam_reports").Scan(&count)
	return count, err
}

// GetCountByHash returns how many times a specific hash has been reported.
func (s *ScamReportStore) GetCountByHash(ctx context.Context, hash string) (int, error) {
	var count int
	err := s.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM scam_reports WHERE number_hash = ?", hash,
	).Scan(&count)
	return count, err
}

// extractPrefix extracts the first 7-10 characters from a hash as a logical prefix.
// Since we're working with hashes (not phone numbers), we use a fixed-length
// prefix of the hash itself. This allows grouping similar-looking hashes
// that may originate from the same phone number prefix.
func extractPrefix(hash string) string {
	if len(hash) < 10 {
		return ""
	}
	// Use first 10 hex chars as the prefix identifier
	// This groups 2^40 possible values — enough granularity
	return hash[:10]
}
