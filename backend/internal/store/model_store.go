package store

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/safering/backend/internal/model"
)

// ModelStore handles CRUD operations for the model_versions table.
type ModelStore struct {
	db *DB
}

// NewModelStore creates a new ModelStore.
func NewModelStore(db *DB) *ModelStore {
	return &ModelStore{db: db}
}

// Insert adds a new model version record.
func (s *ModelStore) Insert(ctx context.Context, mv *model.ModelVersion) error {
	query := `
		INSERT INTO model_versions
		    (version, model_type, file_path, download_url, sha256, file_size_bytes, accuracy, is_active, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)`

	result, err := s.db.ExecContext(ctx, query,
		mv.Version, mv.ModelType, mv.FilePath, mv.DownloadURL,
		mv.SHA256, mv.FileSizeBytes, mv.Accuracy, mv.IsActive,
	)
	if err != nil {
		return fmt.Errorf("insert model version: %w", err)
	}

	id, err := result.LastInsertId()
	if err != nil {
		return fmt.Errorf("get model version id: %w", err)
	}
	mv.ID = id
	return nil
}

// GetActiveByType returns the currently active model for a given type.
func (s *ModelStore) GetActiveByType(ctx context.Context, modelType string) (*model.ModelVersion, error) {
	query := `
		SELECT id, version, model_type, file_path, download_url, sha256,
		       file_size_bytes, accuracy, is_active, created_at
		FROM model_versions
		WHERE model_type = ? AND is_active = 1
		ORDER BY created_at DESC
		LIMIT 1`

	var mv model.ModelVersion
	err := s.db.QueryRowContext(ctx, query, modelType).Scan(
		&mv.ID, &mv.Version, &mv.ModelType, &mv.FilePath, &mv.DownloadURL,
		&mv.SHA256, &mv.FileSizeBytes, &mv.Accuracy, &mv.IsActive, &mv.CreatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, model.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("query active model: %w", err)
	}
	return &mv, nil
}

// GetByVersion retrieves a specific model version by version string and type.
func (s *ModelStore) GetByVersion(ctx context.Context, version, modelType string) (*model.ModelVersion, error) {
	query := `
		SELECT id, version, model_type, file_path, download_url, sha256,
		       file_size_bytes, accuracy, is_active, created_at
		FROM model_versions
		WHERE version = ? AND model_type = ?
		LIMIT 1`

	var mv model.ModelVersion
	err := s.db.QueryRowContext(ctx, query, version, modelType).Scan(
		&mv.ID, &mv.Version, &mv.ModelType, &mv.FilePath, &mv.DownloadURL,
		&mv.SHA256, &mv.FileSizeBytes, &mv.Accuracy, &mv.IsActive, &mv.CreatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, model.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("query model by version: %w", err)
	}
	return &mv, nil
}

// SetActive deactivates all models of the given type, then activates the specified one.
func (s *ModelStore) SetActive(ctx context.Context, id int64, modelType string) error {
	return s.db.WithTx(ctx, func(tx *sql.Tx) error {
		// Deactivate all models of this type
		_, err := tx.ExecContext(ctx,
			"UPDATE model_versions SET is_active = 0 WHERE model_type = ?", modelType)
		if err != nil {
			return fmt.Errorf("deactivate models: %w", err)
		}

		// Activate the specified model
		result, err := tx.ExecContext(ctx,
			"UPDATE model_versions SET is_active = 1 WHERE id = ?", id)
		if err != nil {
			return fmt.Errorf("activate model: %w", err)
		}

		n, _ := result.RowsAffected()
		if n == 0 {
			return model.ErrNotFound
		}
		return nil
	})
}

// ListVersions returns all model versions for a given type, ordered by created_at desc.
func (s *ModelStore) ListVersions(ctx context.Context, modelType string, limit int) ([]*model.ModelVersion, error) {
	if limit <= 0 || limit > 100 {
		limit = 10
	}

	query := `
		SELECT id, version, model_type, file_path, download_url, sha256,
		       file_size_bytes, accuracy, is_active, created_at
		FROM model_versions
		WHERE model_type = ?
		ORDER BY created_at DESC
		LIMIT ?`

	rows, err := s.db.QueryContext(ctx, query, modelType, limit)
	if err != nil {
		return nil, fmt.Errorf("query model versions: %w", err)
	}
	defer rows.Close()

	var versions []*model.ModelVersion
	for rows.Next() {
		var mv model.ModelVersion
		if err := rows.Scan(&mv.ID, &mv.Version, &mv.ModelType, &mv.FilePath, &mv.DownloadURL,
			&mv.SHA256, &mv.FileSizeBytes, &mv.Accuracy, &mv.IsActive, &mv.CreatedAt); err != nil {
			return nil, fmt.Errorf("scan model version row: %w", err)
		}
		versions = append(versions, &mv)
	}
	return versions, rows.Err()
}
