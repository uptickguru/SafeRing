package handler

import (
	"encoding/json"
	"net/http"
	"strings"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/osint"
)

// EnrichmentHandler handles POST /v1/enrich
// Performs OSINT enrichment on a phone number
type EnrichmentHandler struct {
	enrichmentService *osint.EnrichmentService
	logger            *zap.Logger
}

// EnrichmentRequest is the expected JSON body for POST /v1/enrich
type EnrichmentRequest struct {
	PhoneNumber string `json:"phone_number"` // E.164 format: +12125551234
	Hash        string `json:"hash"`         // Optional: if we already have the hash
}

// EnrichmentResponse is the JSON response for POST /v1/enrich
type EnrichmentResponse struct {
	PhoneNumber   string   `json:"phone_number"`
	Carrier       string   `json:"carrier,omitempty"`
	CarrierType   string   `json:"carrier_type,omitempty"`
	CallerName    string   `json:"caller_name,omitempty"`
	IsVOIP        bool     `json:"is_voip"`
	IsKnownProvider bool   `json:"is_known_provider"`
	AccountProvider string `json:"account_provider,omitempty"`
	PotentialAccount bool  `json:"potential_account"`
	RelatedNumbers  []string `json:"related_numbers"`
	SuggestedActions []string `json:"suggested_actions"`
	Hash            string `json:"hash"`
}

// NewEnrichmentHandler creates a new EnrichmentHandler
func NewEnrichmentHandler(enrichmentService *osint.EnrichmentService, logger *zap.Logger) *EnrichmentHandler {
	return &EnrichmentHandler{
		enrichmentService: enrichmentService,
		logger:            logger.Named("handler.enrichment"),
	}
}

// ServeHTTP handles POST /v1/enrich requests
func (h *EnrichmentHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	var req EnrichmentRequest
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request body: "+err.Error())
		return
	}
	defer r.Body.Close()

	// Validate phone number
	if req.PhoneNumber == "" {
		writeError(w, http.StatusBadRequest, "missing required field: phone_number")
		return
	}

	// Normalize to E.164
	phoneNumber := strings.TrimSpace(req.PhoneNumber)
	if !strings.HasPrefix(phoneNumber, "+") {
		phoneNumber = "+" + phoneNumber
	}

	// Validate E.164 format
	if len(phoneNumber) < 8 || len(phoneNumber) > 15 {
		writeError(w, http.StatusBadRequest, "invalid phone number format")
		return
	}

	// Perform enrichment
	result, err := h.enrichmentService.Enrich(r.Context(), phoneNumber)
	if err != nil {
		h.logger.Error("enrichment failed",
			zap.String("number", phoneNumber),
			zap.Error(err))
		writeError(w, http.StatusInternalServerError, "enrichment failed")
		return
	}

	// Build response
	resp := EnrichmentResponse{
		PhoneNumber:      phoneNumber,
		RelatedNumbers:   result.RelatedNumbers,
		SuggestedActions: result.SuggestedActions,
	}

	if result.CarrierInfo != nil {
		resp.Carrier = result.CarrierInfo.Carrier
		resp.CarrierType = result.CarrierInfo.CarrierType
		resp.CallerName = result.CarrierInfo.CallerName
		resp.AccountProvider = result.CarrierInfo.AccountProvider
	}

	resp.IsVOIP = result.IsVOIP
	resp.IsKnownProvider = result.IsKnownProvider
	resp.PotentialAccount = result.PotentialAccount

	// Compute hash if not provided
	if req.Hash != "" {
		resp.Hash = req.Hash
	} else {
		resp.Hash = result.OriginalNumber // Will be hashed in the service
	}

	writeJSON(w, http.StatusOK, resp)

	h.logger.Info("enrichment completed",
		zap.String("number", phoneNumber),
		zap.String("carrier", resp.Carrier),
		zap.Bool("is_voip", resp.IsVOIP),
		zap.Bool("potential_account", resp.PotentialAccount))
}
