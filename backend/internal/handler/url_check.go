package handler

import (
	"net/http"
	"net/url"
	"regexp"
	"strings"

	"go.uber.org/zap"
)

// URLCheckHandler handles GET /v1/check/url
// Analyzes URLs for malicious domains, phishing indicators, and reputation.
type URLCheckHandler struct {
	logger *zap.Logger
}

func NewURLCheckHandler(logger *zap.Logger) *URLCheckHandler {
	return &URLCheckHandler{
		logger: logger.Named("handler.url_check"),
	}
}

func (h *URLCheckHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	urlParam := r.URL.Query().Get("url")
	if urlParam == "" {
		writeError(w, http.StatusBadRequest, "url parameter required")
		return
	}

	parsedURL, err := url.Parse(urlParam)
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid URL format")
		return
	}

	// Analyze URL
	score := 0.0
	indicators := []string{}

	// Check for known phishing domains
	host := strings.ToLower(parsedURL.Host)
	phishingDomains := []string{
		"phishing", "scam", "fraud", "malware", "virus",
		"secure-login", "account-verify", "update-account",
		"paypal-secure", "apple-id-verify", "amazon-account",
	}
	for _, domain := range phishingDomains {
		if strings.Contains(host, domain) {
			score += 0.3
			indicators = append(indicators, "Suspicious domain: "+domain)
		}
	}

	// Check for IP addresses (high risk)
	ipPattern := regexp.MustCompile(`^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$`)
	if ipPattern.MatchString(host) {
		score += 0.4
		indicators = append(indicators, "Raw IP address")
	}

	// Check for excessive subdomains
	parts := strings.Split(host, ".")
	if len(parts) > 4 {
		score += 0.2
		indicators = append(indicators, "Excessive subdomains")
	}

	// Check for URL shorteners
	shorteners := []string{"bit.ly", "tinyurl", "t.co", "goo.gl", "is.gd"}
	for _, s := range shorteners {
		if strings.Contains(host, s) {
			score += 0.15
			indicators = append(indicators, "URL shortener detected")
			break
		}
	}

	// Check for suspicious path patterns
	path := strings.ToLower(parsedURL.Path)
	suspiciousPaths := []string{
		"login", "signin", "verify", "update", "confirm",
		"account", "secure", "banking", "payment",
	}
	for _, pattern := range suspiciousPaths {
		if strings.Contains(path, pattern) {
			score += 0.1
			indicators = append(indicators, "Suspicious path: "+pattern)
		}
	}

	// Check for query parameters (potential data exfiltration)
	if len(parsedURL.Query()) > 5 {
		score += 0.1
		indicators = append(indicators, "Excessive query parameters")
	}

	// Cap score at 1.0
	if score > 1.0 {
		score = 1.0
	}

	// Determine category
	category := "safe"
	if score >= 0.7 {
		category = "dangerous"
	} else if score >= 0.4 {
		category = "suspicious"
	}

	h.logger.Info("URL checked",
		zap.String("url", urlParam),
		zap.Float64("risk_score", score),
		zap.String("category", category),
	)

	response := map[string]interface{}{
		"risk_score":  score,
		"category":    category,
		"indicators":  indicators,
		"url":         urlParam,
		"domain":      host,
	}

	writeJSON(w, http.StatusOK, response)
}
