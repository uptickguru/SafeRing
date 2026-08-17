package handler

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"
)

// MessageFilterHandler serves:
//   POST /v1/message-filter          — Apple network deferral / client classify
//   POST /v1/exceptional/capture     — opt-in encrypted SMS/call bundle for ops OSINT
//   GET  /v1/exceptional/cases       — list case metadata (no decrypt; ops tool uses private key)
//
// Default product path stays hash-only. Exceptional capture requires
// X-SafeRing-Exceptional: 1 and stores ciphertext only.
type MessageFilterHandler struct {
	logger   *zap.Logger
	mu       sync.Mutex
	dataDir  string
	// simple in-memory + file block hints for demo classify
	blockHashes map[string]string // hash -> label
}

func NewMessageFilterHandler(logger *zap.Logger) *MessageFilterHandler {
	dir := os.Getenv("SAFERING_EXCEPTIONAL_DIR")
	if dir == "" {
		dir = filepath.Join(os.TempDir(), "safering-exceptional")
	}
	_ = os.MkdirAll(dir, 0o700)
	h := &MessageFilterHandler{
		logger:      logger.Named("handler.message_filter"),
		dataDir:     dir,
		blockHashes: map[string]string{},
	}
	h.loadBlockHints()
	return h
}

type messageFilterRequest struct {
	SenderFingerprint string                 `json:"sender_fingerprint"`
	SenderDigits      string                 `json:"sender_digits,omitempty"` // optional; prefer fingerprint
	BodyFeatures      map[string]interface{} `json:"body_features"`
	KeywordHits       []string               `json:"keyword_hits"`
	HasURL            bool                   `json:"has_url"`
	Client            map[string]string      `json:"client"`
	// Legacy / simple clients
	ActionHint string `json:"action_hint,omitempty"`
}

type messageFilterResponse struct {
	Action string `json:"action"` // junk|allow|none|promotion|transaction
	Reason string `json:"reason,omitempty"`
}

func (h *MessageFilterHandler) ServeMessageFilter(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	var req messageFilterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		// Apple may send form-ish or empty in some paths — be permissive
		writeJSON(w, http.StatusOK, messageFilterResponse{Action: "none", Reason: "bad_json"})
		return
	}

	fp := strings.ToLower(strings.TrimSpace(req.SenderFingerprint))
	if fp == "" && req.SenderDigits != "" {
		sum := sha256.Sum256([]byte(digitsOnly(req.SenderDigits)))
		fp = hex.EncodeToString(sum[:])
	}

	// Known bad fingerprint
	if fp != "" {
		if label, ok := h.blockHashes[fp]; ok {
			writeJSON(w, http.StatusOK, messageFilterResponse{Action: "junk", Reason: "blocklist:" + label})
			return
		}
		// prefix match 16-char
		if len(fp) >= 16 {
			if label, ok := h.blockHashes[fp[:16]]; ok {
				writeJSON(w, http.StatusOK, messageFilterResponse{Action: "junk", Reason: "blocklist:" + label})
				return
			}
		}
	}

	// Feature-based
	hits := req.KeywordHits
	if hits == nil {
		if v, ok := req.BodyFeatures["keyword_hits"].([]interface{}); ok {
			for _, x := range v {
				if s, ok := x.(string); ok {
					hits = append(hits, s)
				}
			}
		}
	}
	hasURL := req.HasURL
	if v, ok := req.BodyFeatures["has_url"].(bool); ok {
		hasURL = hasURL || v
	}
	if len(hits) > 0 && hasURL {
		writeJSON(w, http.StatusOK, messageFilterResponse{Action: "junk", Reason: "keyword+url"})
		return
	}
	if len(hits) >= 2 {
		writeJSON(w, http.StatusOK, messageFilterResponse{Action: "junk", Reason: "keywords"})
		return
	}

	writeJSON(w, http.StatusOK, messageFilterResponse{Action: "none", Reason: "no_match"})
}

// ExceptionalCaptureRequest is the envelope after client-side encryption.
// Plaintext never required on the wire if ciphertext is present.
type ExceptionalCaptureRequest struct {
	// Required consent markers
	ConsentVersion string `json:"consent_version"` // e.g. "exceptional-v1"
	HouseholdLabel string `json:"household_label,omitempty"` // e.g. "sister-phone" — no real name required

	// Classification aids (unencrypted metadata OK)
	Channel   string `json:"channel"` // sms|call|notification
	CaseTag   string `json:"case_tag,omitempty"`
	CreatedAt string `json:"created_at,omitempty"`

	// Always send hash of sender for blocklist seeding without decrypt
	SenderHash string `json:"sender_hash"`

	// Ciphertext package (base64) — app encrypts with server public key
	// Suggested plaintext JSON: {sender_e164, message_body, note, device_ts}
	CiphertextB64 string `json:"ciphertext_b64"`
	NonceB64      string `json:"nonce_b64,omitempty"`
	Alg           string `json:"alg"` // "chaChaPoly-v1" | "aes-gcm-v1" | "https-only-v0"
	EphemeralPub  string `json:"ephemeral_pub_b64,omitempty"`

	// Optional plaintext fallback ONLY if alg=https-only-v0 and exceptional header set
	// (discouraged; for bring-up). Production should use ciphertext.
	DebugPlain *struct {
		SenderE164  string `json:"sender_e164"`
		MessageBody string `json:"message_body"`
		Note        string `json:"note"`
	} `json:"debug_plain,omitempty"`
}

func (h *MessageFilterHandler) ServeExceptionalCapture(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if r.Header.Get("X-SafeRing-Exceptional") != "1" {
		writeError(w, http.StatusForbidden, "exceptional tier required")
		return
	}
	var req ExceptionalCaptureRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON")
		return
	}
	if req.ConsentVersion == "" || req.SenderHash == "" {
		writeError(w, http.StatusBadRequest, "consent_version and sender_hash required")
		return
	}
	if req.CiphertextB64 == "" && req.DebugPlain == nil {
		writeError(w, http.StatusBadRequest, "ciphertext_b64 or debug_plain required")
		return
	}
	if req.Alg == "" {
		req.Alg = "https-only-v0"
	}
	if req.CreatedAt == "" {
		req.CreatedAt = time.Now().UTC().Format(time.RFC3339)
	}

	id := time.Now().UTC().Format("20060102T150405") + "-" + req.SenderHash[:min(12, len(req.SenderHash))]
	path := filepath.Join(h.dataDir, id+".json")

	// Never log body/plaintext
	record := map[string]interface{}{
		"id":              id,
		"consent_version": req.ConsentVersion,
		"household_label": req.HouseholdLabel,
		"channel":         req.Channel,
		"case_tag":        req.CaseTag,
		"created_at":      req.CreatedAt,
		"sender_hash":     req.SenderHash,
		"alg":             req.Alg,
		"ciphertext_b64":  req.CiphertextB64,
		"nonce_b64":       req.NonceB64,
		"ephemeral_pub":   req.EphemeralPub,
		"has_debug_plain": req.DebugPlain != nil,
	}
	// Store debug plain only if present (bring-up); ops should migrate to ciphertext
	if req.DebugPlain != nil {
		record["debug_plain"] = req.DebugPlain
		// Seed blocklist immediately from digits hash for filter loop
		h.mu.Lock()
		h.blockHashes[strings.ToLower(req.SenderHash)] = "exceptional_pending_osint"
		h.mu.Unlock()
		h.persistBlockHints()
	} else {
		// Still seed hash so filter can junk while OSINT pending
		h.mu.Lock()
		h.blockHashes[strings.ToLower(req.SenderHash)] = "exceptional_encrypted"
		h.mu.Unlock()
		h.persistBlockHints()
	}

	raw, _ := json.MarshalIndent(record, "", "  ")
	if err := os.WriteFile(path, raw, 0o600); err != nil {
		h.logger.Error("write exceptional case", zap.Error(err))
		writeError(w, http.StatusInternalServerError, "store failed")
		return
	}

	h.logger.Info("exceptional capture stored",
		zap.String("id", id),
		zap.String("hash_prefix", req.SenderHash[:min(8, len(req.SenderHash))]),
		zap.String("alg", req.Alg),
		zap.Bool("ciphertext", req.CiphertextB64 != ""),
	)

	writeJSON(w, http.StatusCreated, map[string]interface{}{
		"status":      "accepted",
		"case_id":     id,
		"sender_hash": req.SenderHash,
		"filter":      "seeded_junk_pending_osint",
		"note":        "Ops decrypt offline, OSINT, then POST /v1/exceptional/confirm-block",
	})
}

func (h *MessageFilterHandler) ServeExceptionalCases(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	// Simple protection: require exceptional header (replace with real admin auth later)
	if r.Header.Get("X-SafeRing-Exceptional") != "1" {
		writeError(w, http.StatusForbidden, "exceptional tier required")
		return
	}
	entries, _ := os.ReadDir(h.dataDir)
	var cases []map[string]interface{}
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		if e.Name() == "block_hints.json" {
			continue
		}
		b, err := os.ReadFile(filepath.Join(h.dataDir, e.Name()))
		if err != nil {
			continue
		}
		var m map[string]interface{}
		if json.Unmarshal(b, &m) != nil {
			continue
		}
		// Strip bulky/sensitive fields from list view
		delete(m, "ciphertext_b64")
		delete(m, "debug_plain")
		delete(m, "nonce_b64")
		cases = append(cases, m)
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{"cases": cases, "count": len(cases)})
}

// ConfirmBlock seeds a permanent junk label after OSINT.
func (h *MessageFilterHandler) ServeConfirmBlock(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if r.Header.Get("X-SafeRing-Exceptional") != "1" {
		writeError(w, http.StatusForbidden, "exceptional tier required")
		return
	}
	var req struct {
		SenderHash string `json:"sender_hash"`
		Label      string `json:"label"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.SenderHash == "" {
		writeError(w, http.StatusBadRequest, "sender_hash required")
		return
	}
	if req.Label == "" {
		req.Label = "osint_confirmed_scam"
	}
	h.mu.Lock()
	h.blockHashes[strings.ToLower(req.SenderHash)] = req.Label
	h.mu.Unlock()
	h.persistBlockHints()
	writeJSON(w, http.StatusOK, map[string]string{"status": "blocked", "sender_hash": req.SenderHash, "label": req.Label})
}

func (h *MessageFilterHandler) loadBlockHints() {
	p := filepath.Join(h.dataDir, "block_hints.json")
	b, err := os.ReadFile(p)
	if err != nil {
		return
	}
	var m map[string]string
	if json.Unmarshal(b, &m) == nil {
		h.mu.Lock()
		h.blockHashes = m
		h.mu.Unlock()
	}
}

func (h *MessageFilterHandler) persistBlockHints() {
	h.mu.Lock()
	defer h.mu.Unlock()
	b, _ := json.MarshalIndent(h.blockHashes, "", "  ")
	_ = os.WriteFile(filepath.Join(h.dataDir, "block_hints.json"), b, 0o600)
}

func digitsOnly(s string) string {
	var b strings.Builder
	for _, r := range s {
		if r >= '0' && r <= '9' {
			b.WriteRune(r)
		}
	}
	return b.String()
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
