package store

import (
	"context"
	"fmt"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/safering/backend/internal/config"
	"github.com/safering/backend/internal/model"
	"go.uber.org/zap"
)

// TestIsolation_SeqSequential — Runs against real isolation mechanism (SQLite).
// Tenant A writes, queries only return Tenant A data.

func TestIsolation_SeqSequential(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	logger, _ := zap.NewDevelopment()
	defer logger.Sync()

	db, err := NewDB(ctx, config.DatabaseConfig{
		URL: "file:test_isolation_seq.db",
	}, logger)
	if err != nil {
		t.Fatalf("Failed to create test database: %v", err)
	}
	defer db.Close()
	t.Logf("Database file: test_isolation_seq.db")
	t.Logf("Tenant ID: %s", db.TenantID)
	// Check if database file exists
	if _, err := os.Stat("test_isolation_seq.db"); os.IsNotExist(err) {
		t.Errorf("Database file does not exist: test_isolation_seq.db")
	}

	// Run migrations
	if err := db.RunMigrations(ctx); err != nil {
		t.Fatalf("Failed to run migrations: %v", err)
	}

	// Create store
	snStore := NewScamNumberStore(db)

	// Define tenant A data
	tenantAHash := hashNumber("+1234567890")

	// Define tenant B data
	tenantBHash := hashNumber("+1555555555")

	// Tenant A writes (set tenant ID before write)
	tenantA := &model.ScamNumber{
		NumberHash: tenantAHash,
		Source:     "ftc",
		ScamType:   "IRS",
		RiskScore:  0.9,
	}
	db.TenantID = "tenant_a"
	t.Logf("Tenant ID before Upsert: %s", db.TenantID)
	if err := snStore.Upsert(ctx, tenantA); err != nil {
		t.Errorf("Failed to upsert tenant A: %v", err)
	}
	t.Logf("Tenant ID after Upsert: %s", db.TenantID)

	// Tenant A queries
	t.Logf("Querying for hash: %s", tenantAHash)
	t.Logf("Tenant ID: %s", db.TenantID)
	result, err := snStore.GetByHash(ctx, tenantAHash)
	if err != nil {
		t.Errorf("Failed to get tenant A: %v", err)
	}
	if result == nil {
		t.Error("Tenant A data should be found")
	}
	if result.NumberHash != tenantAHash {
		t.Errorf("Expected hash %s, got %s", tenantAHash, result.NumberHash)
	}

	// Switch to Tenant B
	db.TenantID = "tenant_b"

	// Tenant B should NOT find Tenant A data
	tenantBResult, err := snStore.GetByHash(ctx, tenantAHash)
	if err != nil {
		t.Errorf("Failed to query tenant A hash: %v", err)
	}
	if tenantBResult != nil {
		t.Error("Tenant B should NOT find Tenant A data")
	}

	// Tenant B writes
	tenantB := &model.ScamNumber{
		NumberHash: tenantBHash,
		Source:     "bbb",
		ScamType:   "TechSupport",
		RiskScore:  0.8,
	}
	if err := snStore.Upsert(ctx, tenantB); err != nil {
		t.Errorf("Failed to upsert tenant B: %v", err)
	}

	// Tenant B queries
	result, err = snStore.GetByHash(ctx, tenantBHash)
	if err != nil {
		t.Errorf("Failed to get tenant B: %v", err)
	}
	if result == nil {
		t.Error("Tenant B data should be found")
	}
	if result.NumberHash != tenantBHash {
		t.Errorf("Expected hash %s, got %s", tenantBHash, result.NumberHash)
	}

	// Switch back to Tenant A
	db.TenantID = "tenant_a"

	// Tenant A should NOT find Tenant B data
	tenantAResult, err := snStore.GetByHash(ctx, tenantBHash)
	if err != nil {
		t.Errorf("Failed to query tenant B hash: %v", err)
	}
	if tenantAResult != nil {
		t.Error("Tenant A should NOT find Tenant B data")
	}
}

// TestIsolation_ConcurrentTenants — Runs against real isolation mechanism (SQLite).
// Tenant A and Tenant B write concurrently. Assert zero cross-tenant hits.

func TestIsolation_ConcurrentTenants(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	logger, _ := zap.NewDevelopment()
	defer logger.Sync()

	db, err := NewDB(ctx, config.DatabaseConfig{
		URL: "file:test_isolation_concurrent.db",
	}, logger)
	if err != nil {
		t.Fatalf("Failed to create test database: %v", err)
	}
	defer db.Close()

	// Run migrations
	if err := db.RunMigrations(ctx); err != nil {
		t.Fatalf("Failed to run migrations: %v", err)
	}

	// Create store
	snStore := NewScamNumberStore(db)

	// Define tenant data
	var wg sync.WaitGroup

	// Tenant A writes concurrently
	wg.Add(1)
	go func() {
		defer wg.Done()
		for i := 0; i < 10; i++ {
			hash := fmt.Sprintf("tenant_a_%03d", i)
			sn := &model.ScamNumber{
				NumberHash: hash,
				Source:     "ftc",
				ScamType:   "IRS",
				RiskScore:  0.9,
			}
			db.TenantID = "tenant_a"
			if err := snStore.Upsert(ctx, sn); err != nil {
				t.Errorf("Failed to upsert tenant A record %d: %v", i, err)
			}
		}
	}()

	// Tenant B writes concurrently
	wg.Add(1)
	go func() {
		defer wg.Done()
		for i := 0; i < 10; i++ {
			hash := fmt.Sprintf("tenant_b_%03d", i)
			sn := &model.ScamNumber{
				NumberHash: hash,
				Source:     "bbb",
				ScamType:   "TechSupport",
				RiskScore:  0.8,
			}
			db.TenantID = "tenant_b"
			if err := snStore.Upsert(ctx, sn); err != nil {
				t.Errorf("Failed to upsert tenant B record %d: %v", i, err)
			}
		}
	}()

	// Wait for writes to complete
	wg.Wait()

	// Verify Tenant A can find its own data
	db.TenantID = "tenant_a"
	result, err := snStore.GetByHash(ctx, "tenant_a_000")
	if err != nil {
		t.Errorf("Failed to get tenant A record: %v", err)
	}
	if result == nil {
		t.Error("Tenant A should find its own data")
	}
	if result.Source != "ftc" {
		t.Errorf("Expected source 'ftc', got '%s'", result.Source)
	}

	// Verify Tenant B can find its own data
	db.TenantID = "tenant_b"
	result, err = snStore.GetByHash(ctx, "tenant_b_000")
	if err != nil {
		t.Errorf("Failed to get tenant B record: %v", err)
	}
	if result == nil {
		t.Error("Tenant B should find its own data")
	}
	if result.Source != "bbb" {
		t.Errorf("Expected source 'bbb', got '%s'", result.Source)
	}

	// Switch back to Tenant A
	db.TenantID = "tenant_a"

	// Verify zero cross-tenant hits
	result, err = snStore.GetByHash(ctx, "tenant_b_000")
	if err != nil {
		t.Errorf("Failed to query tenant B hash: %v", err)
	}
	if result != nil {
		t.Error("Tenant A should NOT find Tenant B data")
	}

	// Tenant B should NOT find Tenant A data
	db.TenantID = "tenant_b"
	result, err = snStore.GetByHash(ctx, "tenant_a_000")
	if err != nil {
		t.Errorf("Failed to query tenant A hash: %v", err)
	}
	if result != nil {
		t.Error("Tenant B should NOT find Tenant A data")
	}
}


// hashNumber — Mock hash function for testing.
// In a real implementation, this would be HMAC-SHA256.
func hashNumber(number string) string {
	return fmt.Sprintf("hmac_hash_%s", number)
}
