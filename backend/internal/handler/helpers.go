package handler

import (
	"encoding/json"
	"net/http"
	"regexp"
	"strings"
)

// writeJSON writes a JSON response with the given status code.
func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

// writeError writes a JSON error response.
func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

// isHexString checks if a string contains only valid hex characters.
func isHexString(s string) bool {
	if len(s) == 0 {
		return false
	}
	matched, _ := regexp.MatchString("^[0-9a-fA-F]+$", s)
	return matched
}

// isContentType checks if the request Content-Type matches the expected type.
func isContentType(r *http.Request, expected string) bool {
	ct := r.Header.Get("Content-Type")
	return strings.HasPrefix(ct, expected)
}
