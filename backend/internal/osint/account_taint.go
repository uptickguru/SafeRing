package osint

import (
	"context"
	"fmt"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/carrier"
	"github.com/safering/backend/internal/model"
	"github.com/safering/backend/internal/safecall"
	"github.com/safering/backend/internal/store"
	"github.com/safering/backend/internal/twilio"
)

// AccountTaintService handles tainting all numbers from an account when one number is flagged as scam
// Uses carrier lookup to detect programmable carriers (Twilio, SignalWire, etc.)
// Then queries our own accounts to check if the scammer is using our numbers (spoofing)
// And generates abuse reports to the scammer's carrier
type AccountTaintService struct {
	logger            *zap.Logger
	twilioClient      *twilio.Client
	signalwireClient  *safecall.SWClient
	scamStore         *store.ScamNumberStore
	allowlistStore    *store.AllowlistStore
	clusterDetector   *ClusterDetector
	abuseReportGen    *AbuseReportGenerator
}

// NewAccountTaintService creates a new account taint service
func NewAccountTaintService(
	logger *zap.Logger,
	twilioClient *twilio.Client,
	signalwireClient *safecall.SWClient,
	scamStore *store.ScamNumberStore,
	allowlistStore *store.AllowlistStore,
	clusterDetector *ClusterDetector,
	abuseReportGen *AbuseReportGenerator,
) *AccountTaintService {
	return &AccountTaintService{
		logger:           logger.Named("osint.account_taint"),
		twilioClient:     twilioClient,
		signalwireClient: signalwireClient,
		scamStore:        scamStore,
		allowlistStore:   allowlistStore,
		clusterDetector:  clusterDetector,
		abuseReportGen:   abuseReportGen,
	}
}

// TaintResult contains the results of tainting an account
type TaintResult struct {
	PhoneNumber      string                `json:"phone_number"`
	CarrierInfo      *carrier.LookupResult `json:"carrier_info,omitempty"`
	IsProgrammable   bool                  `json:"is_programmable"`
	IsOurAccount     bool                  `json:"is_our_account"` // True if it's one of OUR numbers (spoofing!)
	Cluster          *Cluster              `json:"cluster,omitempty"`
	AbuseReport      *AbuseReport          `json:"abuse_report,omitempty"`
	ActionTaken      string                `json:"action_taken"`
	Duration         time.Duration         `json:"duration"`
}

// TaintAccountByNumber analyzes a scam number and takes appropriate action
// 1. Carrier lookup to identify the provider
// 2. Check if it's one of our numbers (spoofing detection)
// 3. Detect clusters with other scam numbers
// 4. Generate abuse report if appropriate
func (s *AccountTaintService) TaintAccountByNumber(ctx context.Context, phoneNumber string, scamType string) (*TaintResult, error) {
	start := time.Now()
	result := &TaintResult{
		PhoneNumber: phoneNumber,
	}

	s.logger.Info("starting account taint analysis", zap.String("number", phoneNumber))

	// Step 1: Check if this is one of OUR numbers (allowlist = our owned numbers)
	hash := HashNumber(phoneNumber)
	isOurs, err := s.allowlistStore.IsAllowed(ctx, hash)
	if err != nil {
		s.logger.Warn("allowlist check failed", zap.Error(err))
	}

	if isOurs {
		result.IsOurAccount = true
		result.ActionTaken = "SPOOFING_ALERT: This is one of our numbers being spoofed!"
		s.logger.Warn("🚨 SPOOFING DETECTED: Scammer using our number",
			zap.String("number", phoneNumber))
		result.Duration = time.Since(start)
		return result, nil
	}

	// Step 2: Run cluster detection (includes carrier lookup)
	cluster, err := s.clusterDetector.AnalyzeNumber(ctx, phoneNumber, scamType)
	if err != nil {
		s.logger.Warn("cluster detection failed", zap.Error(err))
	} else {
		result.Cluster = cluster
		result.IsProgrammable = isProgrammableCarrier(cluster.Carrier)

		// Step 3: Generate abuse report if confidence is high enough
		if cluster.Confidence >= 0.7 && cluster.AbuseContact != nil {
			report, err := s.abuseReportGen.GenerateReport(ctx, cluster)
			if err != nil {
				s.logger.Warn("abuse report generation failed", zap.Error(err))
			} else {
				result.AbuseReport = report

				// Step 4: Send report if auto-report is enabled and confidence is high
				if cluster.AutoReport {
					if err := s.abuseReportGen.SendReport(ctx, report); err != nil {
						s.logger.Warn("abuse report send failed", zap.Error(err))
						result.ActionTaken = "abuse_report_generated_not_sent"
					} else {
						result.ActionTaken = "abuse_report_sent"
					}
				} else {
					result.ActionTaken = "abuse_report_generated_manual_review"
				}
			}
		} else {
			result.ActionTaken = "cluster_detected_low_confidence"
		}
	}

	if result.ActionTaken == "" {
		result.ActionTaken = "no_action"
	}

	result.Duration = time.Since(start)

	s.logger.Info("account taint complete",
		zap.String("number", phoneNumber),
		zap.Bool("is_ours", result.IsOurAccount),
		zap.Bool("is_programmable", result.IsProgrammable),
		zap.Int("cluster_size", func() int { if result.Cluster != nil { return len(result.Cluster.Numbers) }; return 0 }()),
		zap.Float64("confidence", func() float64 { if result.Cluster != nil { return result.Cluster.Confidence }; return 0 }()),
		zap.String("action", result.ActionTaken),
		zap.Duration("duration", result.Duration))

	return result, nil
}

// GetOurAccountNumbers returns all numbers we own (from allowlist)
// Useful for checking if a scammer is spoofing us
func (s *AccountTaintService) GetOurAccountNumbers(ctx context.Context) ([]string, error) {
	entries, err := s.allowlistStore.GetAll(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get allowlist: %w", err)
	}

	var numbers []string
	for _, entry := range entries {
		if entry.E164 != "" {
			numbers = append(numbers, entry.E164)
		}
	}

	return numbers, nil
}

// SyncTwilioNumbers fetches all numbers from our Twilio account and adds to allowlist
func (s *AccountTaintService) SyncTwilioNumbers(ctx context.Context) (int, error) {
	if s.twilioClient == nil || !s.twilioClient.Enabled() {
		return 0, fmt.Errorf("twilio not configured")
	}

	numbers, err := s.twilioClient.ListPhoneNumbers(ctx)
	if err != nil {
		return 0, fmt.Errorf("failed to list twilio numbers: %w", err)
	}

	added := 0
	for _, num := range numbers {
		entry := &model.AllowlistEntry{
			E164:     num.PhoneNumber,
			Label:    fmt.Sprintf("Twilio: %s", num.FriendlyName),
			Source:   "twilio",
		}

		if err := s.allowlistStore.Add(ctx, entry); err != nil {
			s.logger.Warn("failed to add twilio number to allowlist",
				zap.String("number", num.PhoneNumber),
				zap.Error(err))
			continue
		}
		added++
	}

	s.logger.Info("twilio numbers synced", zap.Int("added", added), zap.Int("total", len(numbers)))
	return added, nil
}

// SyncSignalWireNumbers fetches all numbers from our SignalWire account and adds to allowlist
func (s *AccountTaintService) SyncSignalWireNumbers(ctx context.Context) (int, error) {
	if s.signalwireClient == nil || !s.signalwireClient.Enabled() {
		return 0, fmt.Errorf("signalwire not configured")
	}

	numbers, err := s.signalwireClient.ListPhoneNumbers(ctx)
	if err != nil {
		return 0, fmt.Errorf("failed to list signalwire numbers: %w", err)
	}

	added := 0
	for _, num := range numbers {
		entry := &model.AllowlistEntry{
			E164:     num.PhoneNumber,
			Label:    fmt.Sprintf("SignalWire: %s", num.FriendlyName),
			Source:   "signalwire",
		}

		if err := s.allowlistStore.Add(ctx, entry); err != nil {
			s.logger.Warn("failed to add signalwire number to allowlist",
				zap.String("number", num.PhoneNumber),
				zap.Error(err))
			continue
		}
		added++
	}

	s.logger.Info("signalwire numbers synced", zap.Int("added", added), zap.Int("total", len(numbers)))
	return added, nil
}
