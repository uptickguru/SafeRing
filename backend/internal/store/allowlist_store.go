package store

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/safering/backend/internal/model"
)

// AllowlistStore manages known-good phone numbers that should never be flagged as scams.
// These are numbers we own, trust, or have verified as legitimate.
type AllowlistStore struct {
	db  *DB
	now func() time.Time
}

// NewAllowlistStore creates a new AllowlistStore.
func NewAllowlistStore(db *DB) *AllowlistStore {
	return &AllowlistStore{db: db, now: time.Now}
}

// IsAllowed checks if a hash is in the allowlist for the current tenant.
func (s *AllowlistStore) IsAllowed(ctx context.Context, hash string) (bool, error) {
	var count int
	err := s.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM allowlist WHERE number_hash = ? AND tenant_id = ?",
		hash, s.db.TenantID).Scan(&count)
	if err != nil {
		return false, fmt.Errorf("check allowlist: %w", err)
	}
	return count > 0, nil
}

// GetByHash returns the allowlist entry for a given hash, or (nil, nil) if not found.
func (s *AllowlistStore) GetByHash(ctx context.Context, hash string) (*model.AllowlistEntry, error) {
	var entry model.AllowlistEntry
	err := s.db.QueryRowContext(ctx,
		`SELECT id, number_hash, tenant_id, e164, label, source, added_at
		 FROM allowlist WHERE number_hash = ? AND tenant_id = ?`,
		hash, s.db.TenantID).Scan(
		&entry.ID, &entry.NumberHash, &entry.TenantID, &entry.E164, &entry.Label, &entry.Source, &entry.AddedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get allowlist entry: %w", err)
	}
	return &entry, nil
}

// Add inserts a number into the allowlist. If it already exists, updates the label and metadata.
func (s *AllowlistStore) Add(ctx context.Context, entry *model.AllowlistEntry) error {
	now := s.now()
	query := `
		INSERT INTO allowlist (number_hash, tenant_id, e164, label, source, added_at)
		VALUES (?, ?, ?, ?, ?, ?)
		ON CONFLICT(number_hash, tenant_id) DO UPDATE SET
			label = excluded.label,
			source = CASE WHEN allowlist.source = 'manual' THEN excluded.source ELSE allowlist.source END,
			added_at = excluded.added_at`

	_, err := s.db.ExecContext(ctx, query,
		entry.NumberHash, s.db.TenantID, entry.E164, entry.Label, entry.Source, now,
	)
	if err != nil {
		return fmt.Errorf("add to allowlist: %w", err)
	}
	return nil
}

// BulkAdd inserts multiple numbers in a transaction.
func (s *AllowlistStore) BulkAdd(ctx context.Context, entries []*model.AllowlistEntry) (int, error) {
	if len(entries) == 0 {
		return 0, nil
	}

	var inserted int
	err := s.db.WithTx(ctx, func(tx *sql.Tx) error {
		stmt, err := tx.PrepareContext(ctx, `
			INSERT OR IGNORE INTO allowlist (number_hash, tenant_id, e164, label, source, added_at)
			VALUES (?, ?, ?, ?, ?, ?)`)
		if err != nil {
			return fmt.Errorf("prepare bulk insert: %w", err)
		}
		defer stmt.Close()

		now := s.now()
		for _, entry := range entries {
			if entry.NumberHash == "" {
				continue
			}
			result, err := stmt.ExecContext(ctx,
				entry.NumberHash, s.db.TenantID, entry.E164, entry.Label, entry.Source, now,
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

// GetAll returns all allowlist entries for the current tenant.
func (s *AllowlistStore) GetAll(ctx context.Context) ([]*model.AllowlistEntry, error) {
	query := `
		SELECT id, number_hash, e164, label, source, added_at
		FROM allowlist
		WHERE tenant_id = ?
		ORDER BY added_at DESC`

	rows, err := s.db.QueryContext(ctx, query, s.db.TenantID)
	if err != nil {
		return nil, fmt.Errorf("query allowlist: %w", err)
	}
	defer rows.Close()

	var results []*model.AllowlistEntry
	for rows.Next() {
		var entry model.AllowlistEntry
		if err := rows.Scan(&entry.ID, &entry.NumberHash, &entry.E164, &entry.Label, &entry.Source, &entry.AddedAt); err != nil {
			return nil, fmt.Errorf("scan allowlist row: %w", err)
		}
		results = append(results, &entry)
	}
	return results, rows.Err()
}

// GetCount returns the total number of allowlist entries for the current tenant.
func (s *AllowlistStore) GetCount(ctx context.Context) (int, error) {
	var count int
	err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM allowlist WHERE tenant_id = ?", s.db.TenantID).Scan(&count)
	return count, err
}

// Remove deletes an entry from the allowlist.
func (s *AllowlistStore) Remove(ctx context.Context, hash string) error {
	_, err := s.db.ExecContext(ctx,
		"DELETE FROM allowlist WHERE number_hash = ? AND tenant_id = ?",
		hash, s.db.TenantID)
	if err != nil {
		return fmt.Errorf("remove from allowlist: %w", err)
	}
	return nil
}
