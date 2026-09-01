package osint

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"
	"time"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/carrier"
	"github.com/safering/backend/internal/store"
)

// Programmable carriers that scammers commonly abuse
var programmableCarriers = map[string]bool{
	"twilio":         true,
	"signalwire":     true,
	"bandwidth.com":  true,
	"bandwidth":      true,
	"vonage":         true,
	"nexmo":          true,
	"plivo":          true,
	"telnyx":         true,
	"sinch":          true,
	"messagebird":    true,
	"bird":           true,
	"google voice":   true,
	"textnow":        true,
	"textfree":       true,
}

// CarrierAbuseContact maps carrier names to abuse reporting channels
var CarrierAbuseContact = map[string]AbuseContact{
	"twilio":        {Email: "abuse@twilio.com", URL: "https://www.twilio.com/en-us/usage/reporting-abuse", ResponseSLA: "24-48h"},
	"signalwire":    {Email: "support@signalwire.com", URL: "https://signalwire.com/support", ResponseSLA: "24-48h"},
	"bandwidth":     {Email: "abuse@bandwidth.com", URL: "https://www.bandwidth.com/report-abuse/", ResponseSLA: "24h"},
	"bandwidth.com": {Email: "abuse@bandwidth.com", URL: "https://www.bandwidth.com/report-abuse/", ResponseSLA: "24h"},
	"vonage":        {Email: "abuse@vonage.com", URL: "https://www.vonage.com/legal/reporting-abuse/", ResponseSLA: "48h"},
	"nexmo":         {Email: "abuse@vonage.com", URL: "https://www.vonage.com/legal/reporting-abuse/", ResponseSLA: "48h"},
	"plivo":         {Email: "abuse@plivo.com", URL: "https://www.plivo.com/legal/abuse-policy", ResponseSLA: "24-48h"},
	"telnyx":        {Email: "abuse@telnyx.com", URL: "https://telnyx.com/legal/abuse", ResponseSLA: "24-48h"},
	"sinch":         {Email: "abuse@sinch.com", URL: "https://www.sinch.com/legal/abuse/", ResponseSLA: "48h"},
	"messagebird":   {Email: "abuse@messagebird.com", URL: "https://messagebird.com/en/legal/abuse", ResponseSLA: "24-48h"},
	"bird":          {Email: "abuse@messagebird.com", URL: "https://messagebird.com/en/legal/abuse", ResponseSLA: "24-48h"},
}

// AbuseContact holds abuse reporting info for a carrier
type AbuseContact struct {
	Email       string `json:"email"`
	URL         string `json:"url"`
	ResponseSLA string `json:"response_sla"`
}

// Cluster represents a group of scam numbers that appear to be from the same operation
type Cluster struct {
	ID           string                `json:"id"`
	ScamType     string                `json:"scam_type"`
	Carrier      string                `json:"carrier"`
	CarrierType  string                `json:"carrier_type"`
	NXXPrefix    string                `json:"nxx_prefix"` // area code + exchange (first 6 digits)
	Numbers      []ClusteredNumber     `json:"numbers"`
	Confidence   float64               `json:"confidence"` // 0.0-1.0
	AutoReport   bool                  `json:"auto_report"` // true if confidence is high enough
	AbuseContact *AbuseContact         `json:"abuse_contact,omitempty"`
	DetectedAt   time.Time             `json:"detected_at"`
}

// ClusteredNumber is a single number within a cluster
type ClusteredNumber struct {
	PhoneNumber string    `json:"phone_number"`
	Hash        string    `json:"hash"`
	RiskScore   float64   `json:"risk_score"`
	CallerName  string    `json:"caller_name,omitempty"`
	ReportedAt  time.Time `json:"reported_at"`
	ReporterID  string    `json:"reporter_id,omitempty"` // anonymized
}

// ClusterDetector finds groups of scam numbers that appear related
type ClusterDetector struct {
	logger        *zap.Logger
	carrierLookup *carrier.LookupService
	scamStore     *store.ScamNumberStore
	reportStore   *store.ScamReportStore

	// Auto-report configuration
	AutoReportEnabled     bool    // master switch
	AutoReportMinNumbers  int     // minimum numbers in cluster before auto-report (default: 3)
	AutoReportMinConfidence float64 // minimum confidence score (default: 0.85)
}

// NewClusterDetector creates a new cluster detector
func NewClusterDetector(
	logger *zap.Logger,
	carrierLookup *carrier.LookupService,
	scamStore *store.ScamNumberStore,
	reportStore *store.ScamReportStore,
) *ClusterDetector {
	return &ClusterDetector{
		logger:                logger.Named("osint.cluster"),
		carrierLookup:         carrierLookup,
		scamStore:             scamStore,
		reportStore:           reportStore,
		AutoReportEnabled:     false, // OFF by default — must be explicitly enabled
		AutoReportMinNumbers:  3,
		AutoReportMinConfidence: 0.85,
	}
}

// AnalyzeNumber runs full OSINT analysis on a newly reported scam number
// Returns any detected cluster
func (d *ClusterDetector) AnalyzeNumber(ctx context.Context, phoneNumber string, scamType string) (*Cluster, error) {
	// Step 1: Carrier lookup
	lookup, err := d.carrierLookup.Lookup(ctx, phoneNumber)
	if err != nil {
		d.logger.Warn("carrier lookup failed for analysis",
			zap.String("number", phoneNumber), zap.Error(err))
		// Continue without carrier info — clustering still works
	}

	// Step 2: Extract NXX (area code + exchange = first 6 digits after country code)
	cleaned := strings.TrimPrefix(phoneNumber, "+")
	cleaned = strings.TrimSpace(cleaned)
	if len(cleaned) < 6 {
		return nil, fmt.Errorf("number too short for NXX extraction")
	}
	nxx := cleaned[:6]

	// Step 3: Build risk indicators
	isVOIP := lookup != nil && strings.ToLower(lookup.CarrierType) == "voip"
	isProgrammable := lookup != nil && isProgrammableCarrier(lookup.Carrier)

	// Step 4: Find related numbers in our DB
	// Query: same NXX prefix + same scam type + last 72 hours
	relatedHashes, err := d.scamStore.FindByPrefixAndType(ctx, nxx, scamType, 72*time.Hour)
	if err != nil {
		d.logger.Warn("failed to find related numbers", zap.Error(err))
	}

	// Step 5: Build cluster
	cluster := &Cluster{
		ID:          generateClusterID(nxx, scamType, lookup),
		ScamType:    scamType,
		NXXPrefix:   nxx,
		DetectedAt:  time.Now(),
	}

	if lookup != nil {
		cluster.Carrier = lookup.Carrier
		cluster.CarrierType = lookup.CarrierType
		if abuse, ok := CarrierAbuseContact[strings.ToLower(lookup.Carrier)]; ok {
			cluster.AbuseContact = &abuse
		}
	}

	// Add the triggering number
	cluster.Numbers = append(cluster.Numbers, ClusteredNumber{
		PhoneNumber: phoneNumber,
		Hash:        HashNumber(phoneNumber),
		RiskScore:   calculateRiskScore(isVOIP, isProgrammable, len(relatedHashes)),
		CallerName:  func() string { if lookup != nil { return lookup.CallerName }; return "" }(),
		ReportedAt:  time.Now(),
	})

	// Add related numbers from DB
	for _, sn := range relatedHashes {
		if sn.NumberHash == HashNumber(phoneNumber) {
			continue // skip the triggering number
		}
		cluster.Numbers = append(cluster.Numbers, ClusteredNumber{
			Hash:       sn.NumberHash,
			ReportedAt: time.Now(), // we don't track exact time per-number in this query
		})
	}

	// Step 6: Calculate confidence
	cluster.Confidence = calculateClusterConfidence(cluster, isVOIP, isProgrammable)

	// Step 7: Determine if auto-report should trigger
	if d.AutoReportEnabled &&
		len(cluster.Numbers) >= d.AutoReportMinNumbers &&
		cluster.Confidence >= d.AutoReportMinConfidence {
		cluster.AutoReport = true
	}

	d.logger.Info("cluster analysis complete",
		zap.String("cluster_id", cluster.ID),
		zap.Int("numbers", len(cluster.Numbers)),
		zap.Float64("confidence", cluster.Confidence),
		zap.Bool("auto_report", cluster.AutoReport),
		zap.String("carrier", cluster.Carrier),
		zap.Bool("is_voip", isVOIP),
		zap.Bool("is_programmable", isProgrammable))

	return cluster, nil
}

// calculateRiskScore combines signals into a risk score
func calculateRiskScore(isVOIP, isProgrammable bool, relatedCount int) float64 {
	score := 0.5 // baseline: it was reported as scam

	if isVOIP {
		score += 0.2
	}
	if isProgrammable {
		score += 0.15
	}
	// Each related number adds confidence
	score += float64(relatedCount) * 0.05

	if score > 1.0 {
		score = 1.0
	}
	return score
}

// calculateClusterConfidence determines how confident we are this is a real campaign
func calculateClusterConfidence(cluster *Cluster, isVOIP, isProgrammable bool) float64 {
	confidence := 0.0

	// Number count (more = more confident)
	switch {
	case len(cluster.Numbers) >= 5:
		confidence += 0.4
	case len(cluster.Numbers) >= 3:
		confidence += 0.3
	case len(cluster.Numbers) >= 2:
		confidence += 0.2
	default:
		confidence += 0.1
	}

	// Same NXX prefix (strong signal)
	if cluster.NXXPrefix != "" {
		confidence += 0.2
	}

	// VOIP carrier (strong signal)
	if isVOIP {
		confidence += 0.15
	}

	// Programmable carrier (medium signal)
	if isProgrammable {
		confidence += 0.15
	}

	// Same scam type across cluster
	if cluster.ScamType != "" {
		confidence += 0.1
	}

	if confidence > 1.0 {
		confidence = 1.0
	}
	return confidence
}

func isProgrammableCarrier(name string) bool {
	return programmableCarriers[strings.ToLower(name)]
}

func generateClusterID(nxx, scamType string, lookup *carrier.LookupResult) string {
	carrierName := "unknown"
	if lookup != nil {
		carrierName = lookup.Carrier
	}
	raw := fmt.Sprintf("%s:%s:%s", nxx, scamType, carrierName)
	hash := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(hash[:8])
}

// HashNumber computes SHA-256 of a phone number
func HashNumber(phone string) string {
	cleaned := strings.TrimSpace(phone)
	cleaned = strings.TrimPrefix(cleaned, "+")
	h := sha256.Sum256([]byte(cleaned))
	return hex.EncodeToString(h[:])
}
