// Package store provides the database access layer for the SafeRing backend.
// It supports SQLite (development) and PostgreSQL (production) backends.
package store

import (
	"context"
	"database/sql"
	"embed"
	"fmt"
	"strings"
	"time"

	_ "github.com/mattn/go-sqlite3"
	"go.uber.org/zap"

	"github.com/safering/backend/internal/config"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

// DB wraps a *sql.DB and provides helper methods for initialization.
type DB struct {
	*sql.DB
	dsn    string
	logger *zap.Logger
}

// NewDB creates a new database connection and runs migrations.
func NewDB(ctx context.Context, cfg config.DatabaseConfig, logger *zap.Logger) (*DB, error) {
	var driverName string
	if cfg.IsSQLite() {
		driverName = "sqlite3"
		// Enable WAL mode and foreign keys for SQLite
		cfg.URL = cfg.URL + "?_journal_mode=WAL&_foreign_keys=on"
	} else {
		driverName = "postgres"
		// Use pgx driver for Postgres — register in future when adding pg support
		return nil, fmt.Errorf("postgres support requires pgx driver; use SQLite for now")
	}

	db, err := sql.Open(driverName, cfg.URL)
	if err != nil {
		return nil, fmt.Errorf("open database: %w", err)
	}

	d := &DB{
		DB:     db,
		dsn:    cfg.URL,
		logger: logger,
	}

	// Configure connection pool
	db.SetMaxOpenConns(cfg.MaxOpenConns)
	db.SetMaxIdleConns(cfg.MaxIdleConns)
	db.SetConnMaxLifetime(cfg.ConnMaxLifetime)

	// Verify connectivity
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, fmt.Errorf("ping database: %w", err)
	}

	logger.Info("database connected",
		zap.String("driver", driverName),
		zap.Int("max_open", cfg.MaxOpenConns),
	)

	return d, nil
}

// RunMigrations applies database migrations from the embedded migrations directory.
// Migrations are applied in filename order and tracked in a schema_migrations table.
func (d *DB) RunMigrations(ctx context.Context) error {
	// Create migrations tracking table
	if _, err := d.ExecContext(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version     INTEGER PRIMARY KEY,
			filename    TEXT NOT NULL,
			applied_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
		)
	`); err != nil {
		return fmt.Errorf("create migration tracking table: %w", err)
	}

	// Read migration files from embedded FS
	entries, err := migrationsFS.ReadDir("migrations")
	if err != nil {
		return fmt.Errorf("read migrations directory: %w", err)
	}

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sql") {
			continue
		}

		// Extract version from filename (e.g., "001_initial.sql" -> version 1)
		parts := strings.SplitN(entry.Name(), "_", 2)
		if len(parts) < 2 {
			continue
		}

		var version int
		if _, err := fmt.Sscanf(parts[0], "%d", &version); err != nil {
			continue
		}

		// Check if migration has already been applied
		var count int
		if err := d.QueryRowContext(ctx, "SELECT COUNT(*) FROM schema_migrations WHERE version = ?", version).Scan(&count); err != nil {
			return fmt.Errorf("check migration %d: %w", version, err)
		}
		if count > 0 {
			d.logger.Debug("migration already applied", zap.String("file", entry.Name()))
			continue
		}

		// Read and apply migration
		content, err := migrationsFS.ReadFile("migrations/" + entry.Name())
		if err != nil {
			return fmt.Errorf("read migration %s: %w", entry.Name(), err)
		}

		if _, err := d.ExecContext(ctx, string(content)); err != nil {
			return fmt.Errorf("apply migration %s: %w", entry.Name(), err)
		}

		// Record migration
		if _, err := d.ExecContext(ctx,
			"INSERT INTO schema_migrations (version, filename) VALUES (?, ?)",
			version, entry.Name(),
		); err != nil {
			return fmt.Errorf("record migration %s: %w", entry.Name(), err)
		}

		d.logger.Info("applied migration", zap.String("file", entry.Name()))
	}

	return nil
}

// WithTx executes a function within a database transaction.
// The transaction is committed if the function returns nil, or rolled back on error.
func (d *DB) WithTx(ctx context.Context, fn func(tx *sql.Tx) error) error {
	tx, err := d.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin transaction: %w", err)
	}

	defer func() {
		if p := recover(); p != nil {
			tx.Rollback()
			panic(p) // re-throw after rollback
		}
	}()

	if err := fn(tx); err != nil {
		if rbErr := tx.Rollback(); rbErr != nil {
			return fmt.Errorf("rollback failed: %v (original error: %w)", rbErr, err)
		}
		return err
	}

	return tx.Commit()
}

// HealthCheck returns nil if the database is reachable, or an error.
func (d *DB) HealthCheck(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	return d.PingContext(ctx)
}

// Close gracefully shuts down the database connection.
func (d *DB) Close() error {
	d.logger.Info("closing database connection")
	return d.DB.Close()
}
