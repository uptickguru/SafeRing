package osint

import (
	"context"
	"fmt"
	"strings"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/carrier"
	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/store"
)

// EnrichmentService performs OSINT enrichment on scam numbers
type EnrichmentService struct {
	logger         *zap.Logger
	carrierLookup  *carrier.LookupService
	scamStore      *store.ScamNumberStore
	allowlistStore *store.AllowlistStore
}

// NewEnrichmentService creates a new OSINT enrichment service
func NewEnrichmentService(
	logger *zap.Logger,
	carrierLookup *carrier.LookupService,
	scamStore *store.ScamNumberStore,
	allowlistStore *store.AllowlistStore,
) *EnrichmentService {
	return &EnrichmentService{
		logger:         logger.Named("osint.enrichment"),
		carrierLookup:  carrierLookup,
		scamStore:      scamStore,
		allowlistStore: allowlistStore,
	}
}

// EnrichmentResult contains the results of OSINT enrichment
type EnrichmentResult struct {
	OriginalNumber    string                    `json:"original_number"`
	CarrierInfo       *carrier.LookupResult     `json:"carrier_info"`
	IsVOIP            bool                      `json:"is_voip"`
	IsKnownProvider   bool                      `json:"is_known_provider"`
	PotentialAccount  bool                      `json:"potential_account"`
	RelatedNumbers    []string                  `json:"related_numbers"`
	SuggestedActions  []string                  `json:"suggested_actions"`
}

// Enrich performs OSINT enrichment on a scam number
func (e *EnrichmentService) Enrich(ctx context.Context, phoneNumber string) (*EnrichmentResult, error) {
	result := &EnrichmentResult{
		OriginalNumber: phoneNumber,
	}

	// Step 1: Carrier lookup
	carrierInfo, err := e.carrierLookup.Lookup(ctx, phoneNumber)
	if err != nil {
		e.logger.Warn("carrier lookup failed",
			zap.String("number", phoneNumber),
			zap.Error(err))
		// Continue with enrichment even if carrier lookup fails
	} else {
		result.CarrierInfo = carrierInfo
		result.IsVOIP = carrierInfo.CarrierType == "voip"
		result.IsKnownProvider = carrierInfo.AccountProvider != ""

		// If it's from Twilio or SignalWire, it's likely a scam operation
		// (legitimate businesses usually have proper business lines)
		if carrierInfo.AccountProvider == "twilio" || carrierInfo.AccountProvider == "signalwire" {
			result.PotentialAccount = true
			result.SuggestedActions = append(result.SuggestedActions,
				"Check for related numbers in same NXX block",
				"Report to carrier abuse department",
				"Monitor for pattern in caller name")
		}

		// VOIP numbers are more likely to be used for scams
		if result.IsVOIP {
			result.SuggestedActions = append(result.SuggestedActions,
				"High risk: VOIP number",
				"Check for spoofing indicators")
		}
	}

	// Step 2: Find related numbers in the same NXX block
	// NXX = area code (NPA) + exchange (first 3 digits after area code)
	// Numbers from the same scam operation often share the same NXX
	relatedNumbers, err := e.findRelatedNumbers(ctx, phoneNumber)
	if err != nil {
		e.logger.Warn("failed to find related numbers", zap.Error(err))
	} else {
		result.RelatedNumbers = relatedNumbers
		if len(relatedNumbers) > 0 {
			result.SuggestedActions = append(result.SuggestedActions,
				fmt.Sprintf("Found %d related numbers in same NXX block", len(relatedNumbers)))
		}
	}

	// Step 3: Check if number is in our allowlist (potential spoofing)
	if e.allowlistStore != nil {
		hash := model.HashPhoneNumber(phoneNumber)
		allowed, err := e.allowlistStore.IsAllowed(ctx, hash)
		if err == nil && allowed {
			result.SuggestedActions = append(result.SuggestedActions,
				"⚠️ CRITICAL: Number is in our allowlist - possible spoofing or account compromise!")
		}
	}

	return result, nil
}

// findRelatedNumbers finds other scam numbers in the same NXX block
// NXX = first 6 digits: area code (3) + exchange (3)
func (e *EnrichmentService) findRelatedNumbers(ctx context.Context, phoneNumber string) ([]string, error) {
	// Extract NXX (first 6 digits after country code)
	cleaned := strings.TrimPrefix(phoneNumber, "+")
	cleaned = strings.TrimSpace(cleaned)

	if len(cleaned) < 6 {
		return nil, fmt.Errorf("number too short for NXX extraction")
	}

	// nxx := cleaned[:6] // e.g., "1212555" from "+12125551234"

	// Query the scam database for numbers with the same NXX
	// This is a heuristic: scam operations often use blocks of numbers
	// from the same exchange

	// For now, return empty list
	// TODO: Implement NXX-based query in scam store
	// Query: SELECT DISTINCT number_hash FROM scam_numbers 
	//        WHERE number_hash LIKE 'hash_prefix_for_nxx%'

	return []string{}, nil
}

// ReportToCarrier sends an abuse report to the carrier
func (e *EnrichmentService) ReportToCarrier(ctx context.Context, phoneNumber string, reason string) error {
	// TODO: Implement carrier-specific abuse reporting
	// Twilio: https://www.twilio.com/docs/usage/reporting-abuse
	// SignalWire: Contact support@signalwire.com

	e.logger.Info("abuse report would be sent to carrier",
		zap.String("number", phoneNumber),
		zap.String("reason", reason))

	return nil
}

// BatchEnrich enriches multiple numbers
func (e *EnrichmentService) BatchEnrich(ctx context.Context, phoneNumbers []string) ([]*EnrichmentResult, error) {
	results := make([]*EnrichmentResult, 0, len(phoneNumbers))

	for _, number := range phoneNumbers {
		result, err := e.Enrich(ctx, number)
		if err != nil {
			e.logger.Warn("enrichment failed",
				zap.String("number", number),
				zap.Error(err))
			continue
		}
		results = append(results, result)

		// Rate limit: don't hammer the carrier APIs
		time.Sleep(100 * time.Millisecond)
	}

	return results, nil
}
