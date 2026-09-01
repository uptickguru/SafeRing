package store

import (
	"context"
	"fmt"
	"sync"
	"testing"
	"time"

	"github.com/safering/backend/internal/config"
	"github.com/safering/backend/internal/model"
	"go.uber.org/zap"
)

func TestIsolation_SeqSequential(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	logger, _ := zap.NewDevelopment()
	defer logger.Sync()

	db, err := NewDB(ctx, config.DatabaseConfig{
		URL: t.TempDir() + "/test.db",
	}, logger)
	if err != nil {
		t.Fatalf("Failed to create test database: %v", err)
	}
	defer db.Close()

	if err := db.RunMigrations(ctx); err != nil {
		t.Fatalf("Failed to run migrations: %v", err)
	}

	snStore := NewScamNumberStore(db)

	tenantAHash := "hmac_hash_+1234567890"
	tenantBHash := "hmac_hash_+1555555555"

	// Tenant A writes
	db.TenantID = "tenant_a"
	tenantA := &model.ScamNumber{
		NumberHash: tenantAHash,
		Source:     "ftc",
		ScamType:   "IRS",
		RiskScore:  0.9,
	}
	if err := snStore.Upsert(ctx, tenantA); err != nil {
		t.Fatalf("Failed to upsert tenant A: %v", err)
	}

	// Tenant A queries own data
	result, err := snStore.GetByHash(ctx, tenantAHash)
	if err != nil {
		t.Fatalf("Failed to get tenant A: %v", err)
	}
	if result == nil {
		t.Fatal("Tenant A data should be found")
	}
	if result.NumberHash != tenantAHash {
		t.Errorf("Expected hash %s, got %s", tenantAHash, result.NumberHash)
	}
	if result.Source != "ftc" {
		t.Errorf("Expected source 'ftc', got '%s'", result.Source)
	}

	// Tenant B should NOT find Tenant A data
	db.TenantID = "tenant_b"
	tenantBResult, err := snStore.GetByHash(ctx, tenantAHash)
	if err != nil {
		t.Errorf("Query error for tenant B: %v", err)
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
		t.Fatalf("Failed to upsert tenant B: %v", err)
	}

	// Tenant B queries own data
	result, err = snStore.GetByHash(ctx, tenantBHash)
	if err != nil {
		t.Fatalf("Failed to get tenant B: %v", err)
	}
	if result == nil {
		t.Fatal("Tenant B data should be found")
	}
	if result.Source != "bbb" {
		t.Errorf("Expected source 'bbb', got '%s'", result.Source)
	}

	// Tenant A should NOT find Tenant B data
	db.TenantID = "tenant_a"
	tenantAResult, err := snStore.GetByHash(ctx, tenantBHash)
	if err != nil {
		t.Errorf("Query error for tenant A: %v", err)
	}
	if tenantAResult != nil {
		t.Error("Tenant A should NOT find Tenant B data")
	}

	// Verify Tenant A can still find their own data
	result, err = snStore.GetByHash(ctx, tenantAHash)
	if err != nil {
		t.Fatalf("Failed to get tenant A again: %v", err)
	}
	if result == nil {
		t.Fatal("Tenant A data should still be found")
	}
}

func TestIsolation_ConcurrentTenants(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	logger, _ := zap.NewDevelopment()
	defer logger.Sync()

	db, err := NewDB(ctx, config.DatabaseConfig{
		URL: t.TempDir() + "/test.db",
	}, logger)
	if err != nil {
		t.Fatalf("Failed to create test database: %v", err)
	}
	defer db.Close()

	if err := db.RunMigrations(ctx); err != nil {
		t.Fatalf("Failed to run migrations: %v", err)
	}

	snStore := NewScamNumberStore(db)

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

	wg.Wait()

	// Verify Tenant A can find its own data
	db.TenantID = "tenant_a"
	result, err := snStore.GetByHash(ctx, "tenant_a_000")
	if err != nil {
		t.Errorf("Failed to get tenant A record: %v", err)
	}
	if result == nil {
		t.Error("Tenant A should find its own data")
	} else if result.Source != "ftc" {
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
	} else if result.Source != "bbb" {
		t.Errorf("Expected source 'bbb', got '%s'", result.Source)
	}

	// Verify zero cross-tenant hits
	db.TenantID = "tenant_a"
	result, err = snStore.GetByHash(ctx, "tenant_b_000")
	if err != nil {
		t.Errorf("Query error: %v", err)
	}
	if result != nil {
		t.Error("Tenant A should NOT find Tenant B data")
	}

	db.TenantID = "tenant_b"
	result, err = snStore.GetByHash(ctx, "tenant_a_000")
	if err != nil {
		t.Errorf("Query error: %v", err)
	}
	if result != nil {
		t.Error("Tenant B should NOT find Tenant A data")
	}

	// Verify counts
	db.TenantID = "tenant_a"
	countA, err := snStore.GetCount(ctx)
	if err != nil {
		t.Fatalf("GetCount tenant A: %v", err)
	}
	if countA != 10 {
		t.Errorf("Expected 10 records for tenant A, got %d", countA)
	}

	db.TenantID = "tenant_b"
	countB, err := snStore.GetCount(ctx)
	if err != nil {
		t.Fatalf("GetCount tenant B: %v", err)
	}
	if countB != 10 {
		t.Errorf("Expected 10 records for tenant B, got %d", countB)
	}
}
