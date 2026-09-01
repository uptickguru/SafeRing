package handler

import (
	"encoding/json"
	"net/http"
	"net/url"
	"regexp"
	"strings"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/model"
)

// EmailCheckHandler handles POST /v1/check/email
// Analyzes email content for phishing links, malicious attachments, and suspicious senders.
type EmailCheckHandler struct {
	logger *zap.Logger
}

func NewEmailCheckHandler(logger *zap.Logger) *EmailCheckHandler {
	return &EmailCheckHandler{
		logger: logger.Named("handler.email_check"),
	}
}

func (h *EmailCheckHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if !isContentType(r, "application/json") {
		writeError(w, http.StatusBadRequest, "Content-Type must be application/json")
		return
	}

	var req model.EmailCheckRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	// Validate at least some content
	if req.Body == "" && req.Sender == "" && len(req.AttachmentHashes) == 0 {
		writeError(w, http.StatusBadRequest, "email body, sender, or attachment hashes required")
		return
	}

	// Run phishing analysis
	resp := h.analyzeEmail(req)

	writeJSON(w, http.StatusOK, resp)

	h.logger.Info("email check completed",
		zap.String("sender_domain", extractDomain(req.Sender)),
		zap.Float64("risk_score", resp.RiskScore),
		zap.Bool("is_phishing", resp.IsPhishing),
		zap.Int("indicators", len(resp.Indicators)),
	)
}

// analyzeEmail runs all phishing detection checks on the email.
func (h *EmailCheckHandler) analyzeEmail(req model.EmailCheckRequest) model.EmailCheckResponse {
	var indicators []model.PhishingIndicator
	var maliciousURLs []string
	riskScore := 0.0

	// 1. Sender analysis
	senderScore, senderIndicators := h.analyzeSender(req.Sender)
	riskScore += senderScore
	indicators = append(indicators, senderIndicators...)

	// 2. URL extraction and analysis
	urlScore, urlIndicators, urls := h.analyzeURLs(req.Body)
	riskScore += urlScore
	indicators = append(indicators, urlIndicators...)
	maliciousURLs = append(maliciousURLs, urls...)

	// 3. Keyword / social engineering pattern matching
	keywordScore, keywordIndicators := h.analyzeKeywords(req.Body, req.Subject)
	riskScore += keywordScore
	indicators = append(indicators, keywordIndicators...)

	// 4. Attachment hash analysis
	attachmentScore, attachmentIndicators := h.analyzeAttachments(req.AttachmentHashes)
	riskScore += attachmentScore
	indicators = append(indicators, attachmentIndicators...)

	// Clamp risk score to [0, 1]
	if riskScore > 1.0 {
		riskScore = 1.0
	}

	// Determine sender reputation
	senderReputation := h.getSenderReputation(req.Sender)

	// Determine attachment risk
	attachmentRisk := "none"
	if len(req.AttachmentHashes) > 0 {
		if attachmentScore > 0.7 {
			attachmentRisk = "high"
		} else if attachmentScore > 0.3 {
			attachmentRisk = "medium"
		} else {
			attachmentRisk = "low"
		}
	}

	// Determine phishing type
	phishingType := ""
	isPhishing := riskScore >= 0.6
	if isPhishing {
		phishingType = classifyPhishingType(indicators)
	}

	// Recommendation
	recommendation := "Email appears safe"
	if riskScore >= 0.8 {
		recommendation = "Do not interact — report and delete"
	} else if riskScore >= 0.6 {
		recommendation = "Proceed with caution — verify sender independently"
	} else if riskScore >= 0.3 {
		recommendation = "Some suspicious signals — review carefully"
	}

	return model.EmailCheckResponse{
		RiskScore:        riskScore,
		IsPhishing:       isPhishing,
		PhishingType:     phishingType,
		Indicators:       indicators,
		MaliciousURLs:    maliciousURLs,
		SenderReputation: senderReputation,
		AttachmentRisk:   attachmentRisk,
		Recommendation:   recommendation,
	}
}

// analyzeSender checks the sender email for suspicious patterns.
func (h *EmailCheckHandler) analyzeSender(sender string) (float64, []model.PhishingIndicator) {
	var indicators []model.PhishingIndicator
	score := 0.0

	if sender == "" {
		return 0.0, indicators
	}

	senderLower := strings.ToLower(sender)

	// Check for spoofed domains (typosquatting of common brands)
	suspiciousDomains := []string{
		"paypal", "apple", "google", "microsoft", "amazon", "netflix",
		"chase", "wells", "bankofamerica", "irs", "fedex", "ups", "dhl",
	}

	domain := extractDomain(senderLower)
	for _, brand := range suspiciousDomains {
		if strings.Contains(domain, brand) && !strings.HasPrefix(domain, brand+".") && domain != brand+".com" {
			indicators = append(indicators, model.PhishingIndicator{
				Type:        "sender",
				Severity:    "high",
				Description: "Sender domain resembles " + brand + " but is not an official domain",
				Confidence:  0.85,
			})
			score += 0.4
			break
		}
	}

	// Check for free email providers impersonating businesses
	freeProviders := []string{"gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "protonmail.com", "aol.com"}
	for _, provider := range freeProviders {
		if strings.HasSuffix(domain, provider) {
			// Check if local part tries to impersonate a business
			localPart := strings.Split(senderLower, "@")[0]
			businessTerms := []string{"support", "billing", "security", "alert", "verify", "account", "admin", "service"}
			for _, term := range businessTerms {
				if strings.Contains(localPart, term) {
					indicators = append(indicators, model.PhishingIndicator{
						Type:        "sender",
						Severity:    "medium",
						Description: "Business-like sender name using free email provider",
						Confidence:  0.7,
					})
					score += 0.25
					break
				}
			}
			break
		}
	}

	// Check for IP-based or suspicious domain patterns
	if matched, _ := regexp.MatchString(`\d+\.\d+\.\d+\.\d+`, domain); matched {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "sender",
			Severity:    "high",
			Description: "Sender domain uses IP address format",
			Confidence:  0.95,
		})
		score += 0.5
	}

	// Check for excessive subdomains (e.g., paypal.secure.login.evil.com)
	subdomainCount := strings.Count(domain, ".")
	if subdomainCount > 3 {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "sender",
			Severity:    "medium",
			Description: "Sender domain has excessive subdomains",
			Confidence:  0.6,
		})
		score += 0.2
	}

	return score, indicators
}

// analyzeURLs extracts and checks URLs from the email body.
func (h *EmailCheckHandler) analyzeURLs(body string) (float64, []model.PhishingIndicator, []string) {
	var indicators []model.PhishingIndicator
	var maliciousURLs []string
	score := 0.0

	if body == "" {
		return 0.0, indicators, maliciousURLs
	}

	// Extract URLs
	urlPattern := regexp.MustCompile(`https?://[^\s<>"']+`)
	urls := urlPattern.FindAllString(body, -1)

	for _, rawURL := range urls {
		parsed, err := url.Parse(rawURL)
		if err != nil {
			continue
		}

		host := strings.ToLower(parsed.Host)

		// Check for IP-based URLs
		if matched, _ := regexp.MatchString(`\d+\.\d+\.\d+\.\d+`, host); matched {
			indicators = append(indicators, model.PhishingIndicator{
				Type:        "url",
				Severity:    "high",
				Description: "URL uses IP address instead of domain name",
				Confidence:  0.9,
			})
			maliciousURLs = append(maliciousURLs, rawURL)
			score += 0.3
			continue
		}

		// Check for URL shorteners (hiding destination)
		shorteners := []string{"bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "buff.ly"}
		for _, s := range shorteners {
			if host == s {
				indicators = append(indicators, model.PhishingIndicator{
					Type:        "url",
					Severity:    "medium",
					Description: "URL uses a link shortener — destination is hidden",
					Confidence:  0.6,
				})
				score += 0.15
				break
			}
		}

		// Check for suspicious TLDs
		suspiciousTLDs := []string{".tk", ".ml", ".ga", ".cf", ".gq", ".xyz", ".top", ".click", ".download"}
		for _, tld := range suspiciousTLDs {
			if strings.HasSuffix(host, tld) {
				indicators = append(indicators, model.PhishingIndicator{
					Type:        "url",
					Severity:    "medium",
					Description: "URL uses suspicious TLD (" + tld + ")",
					Confidence:  0.65,
				})
				maliciousURLs = append(maliciousURLs, rawURL)
				score += 0.2
				break
			}
		}

		// Check for excessive subdomains (phishers use many subdomains)
		subdomainCount := strings.Count(host, ".")
		if subdomainCount > 4 {
			indicators = append(indicators, model.PhishingIndicator{
				Type:        "url",
				Severity:    "high",
				Description: "URL has excessive subdomains — may be spoofed",
				Confidence:  0.75,
			})
			maliciousURLs = append(maliciousURLs, rawURL)
			score += 0.25
		}
	}

	// Check for display text mismatch (shows safe URL, links to malicious)
	// Pattern: [safe text](http://evil.com) or "safe text http://evil.com"
	displayURLPattern := regexp.MustCompile(`(?:https?://[a-zA-Z0-9.-]+\.[a-z]{2,})\s+https?://`)
	if displayURLPattern.MatchString(body) {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "url",
			Severity:    "high",
			Description: "Possible URL display mismatch — shown text differs from actual link",
			Confidence:  0.7,
		})
		score += 0.3
	}

	return score, indicators, maliciousURLs
}

// analyzeKeywords checks for social engineering and phishing keywords.
func (h *EmailCheckHandler) analyzeKeywords(body, subject string) (float64, []model.PhishingIndicator) {
	var indicators []model.PhishingIndicator
	score := 0.0

	combined := strings.ToLower(body + " " + subject)

	// Urgency / pressure tactics
	urgencyKeywords := []string{
		"act now", "immediate action", "your account will be", "suspended",
		"verify your", "confirm your identity", "unusual activity",
		"unauthorized transaction", "security alert", "account locked",
		"within 24 hours", "expires today", "limited time", "respond immediately",
	}

	urgencyCount := 0
	for _, kw := range urgencyKeywords {
		if strings.Contains(combined, kw) {
			urgencyCount++
		}
	}
	if urgencyCount >= 3 {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "keyword",
			Severity:    "high",
			Description: "Multiple urgency/pressure tactics detected",
			Confidence:  0.8,
		})
		score += 0.3
	} else if urgencyCount >= 1 {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "keyword",
			Severity:    "low",
			Description: "Urgency language detected",
			Confidence:  0.4,
		})
		score += 0.1
	}

	// Financial information requests
	financialKeywords := []string{
		"social security", "credit card", "bank account", "routing number",
		"account number", "cvv", "pin code", "password", "security question",
		"wire transfer", "gift card", "bitcoin", "cryptocurrency",
	}

	financialCount := 0
	for _, kw := range financialKeywords {
		if strings.Contains(combined, kw) {
			financialCount++
		}
	}
	if financialCount >= 2 {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "keyword",
			Severity:    "critical",
			Description: "Requests for sensitive financial information",
			Confidence:  0.9,
		})
		score += 0.4
	} else if financialCount >= 1 {
		score += 0.15
	}

	// Credential harvesting
	credentialKeywords := []string{
		"sign in", "log in", "enter your password", "reset your password",
		"update your account", "verify your email", "confirm your login",
	}

	credentialCount := 0
	for _, kw := range credentialKeywords {
		if strings.Contains(combined, kw) {
			credentialCount++
		}
	}
	if credentialCount >= 2 {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "keyword",
			Severity:    "high",
			Description: "Credential harvesting patterns detected",
			Confidence:  0.85,
		})
		score += 0.35
	}

	// Prize / lottery scams
	prizeKeywords := []string{
		"you've won", "congratulations", "lottery", "prize", "jackpot",
		"selected as winner", "claim your", "free gift",
	}

	for _, kw := range prizeKeywords {
		if strings.Contains(combined, kw) {
			indicators = append(indicators, model.PhishingIndicator{
				Type:        "keyword",
				Severity:    "medium",
				Description: "Prize/lottery scam language detected",
				Confidence:  0.7,
			})
			score += 0.25
			break
		}
	}

	// Impersonation of authority
	authorityKeywords := []string{
		"irs", "tax refund", "fbi", "law enforcement", "court summons",
		"social security administration", "medicare", "government",
	}

	for _, kw := range authorityKeywords {
		if strings.Contains(combined, kw) {
			indicators = append(indicators, model.PhishingIndicator{
				Type:        "keyword",
				Severity:    "high",
				Description: "Impersonation of government authority detected",
				Confidence:  0.75,
			})
			score += 0.3
			break
		}
	}

	return score, indicators
}

// analyzeAttachments checks attachment hashes against known malicious file signatures.
func (h *EmailCheckHandler) analyzeAttachments(hashes []string) (float64, []model.PhishingIndicator) {
	var indicators []model.PhishingIndicator
	score := 0.0

	if len(hashes) == 0 {
		return 0.0, indicators
	}

	// TODO: In production, check hashes against a malicious file database (VirusTotal, etc.)
	// For now, flag the presence of attachments as a risk factor
	for _, hash := range hashes {
		if len(hash) != 64 || !isHexString(hash) {
			indicators = append(indicators, model.PhishingIndicator{
				Type:        "attachment",
				Severity:    "medium",
				Description: "Invalid attachment hash format",
				Confidence:  0.5,
			})
			score += 0.1
		}
	}

	// Multiple attachments increase risk
	if len(hashes) > 3 {
		indicators = append(indicators, model.PhishingIndicator{
			Type:        "attachment",
			Severity:    "low",
			Description: "Multiple attachments detected",
			Confidence:  0.4,
		})
		score += 0.1
	}

	return score, indicators
}

// getSenderReputation determines the sender's reputation category.
func (h *EmailCheckHandler) getSenderReputation(sender string) string {
	if sender == "" {
		return "unknown"
	}

	domain := extractDomain(strings.ToLower(sender))

	// Known trusted domains (major providers, common businesses)
	trustedDomains := []string{
		"gmail.com", "google.com", "apple.com", "microsoft.com",
		"amazon.com", "netflix.com", "paypal.com",
	}

	for _, td := range trustedDomains {
		if domain == td {
			return "trusted"
		}
	}

	// Free email providers with business impersonation → suspicious
	freeProviders := []string{"gmail.com", "yahoo.com", "hotmail.com", "outlook.com"}
	localPart := strings.Split(strings.ToLower(sender), "@")[0]
	for _, fp := range freeProviders {
		if domain == fp {
			businessTerms := []string{"support", "billing", "security", "admin", "service", "noreply"}
			for _, bt := range businessTerms {
				if strings.Contains(localPart, bt) {
					return "suspicious"
				}
			}
		}
	}

	return "unknown"
}

// extractDomain extracts the domain part from an email address.
func extractDomain(email string) string {
	parts := strings.Split(email, "@")
	if len(parts) != 2 {
		return email
	}
	return strings.TrimSpace(parts[1])
}

// classifyPhishingType determines the primary phishing category from indicators.
func classifyPhishingType(indicators []model.PhishingIndicator) string {
	types := map[string]int{
		"credential_harvesting": 0,
		"financial_fraud":       0,
		"authority_impersonation": 0,
		"prize_scam":            0,
		"spear_phishing":        0,
	}

	for _, ind := range indicators {
		switch ind.Type {
		case "keyword":
			if strings.Contains(ind.Description, "credential") || strings.Contains(ind.Description, "Credential") {
				types["credential_harvesting"]++
			}
			if strings.Contains(ind.Description, "financial") || strings.Contains(ind.Description, "Financial") {
				types["financial_fraud"]++
			}
			if strings.Contains(ind.Description, "authority") || strings.Contains(ind.Description, "government") {
				types["authority_impersonation"]++
			}
			if strings.Contains(ind.Description, "prize") || strings.Contains(ind.Description, "lottery") {
				types["prize_scam"]++
			}
		case "url":
			types["credential_harvesting"]++
		case "sender":
			types["spear_phishing"]++
		}
	}

	maxCount := 0
	maxType := "generic"
	for t, c := range types {
		if c > maxCount {
			maxCount = c
			maxType = t
		}
	}

	return maxType
}
