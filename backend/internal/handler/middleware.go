// Package handler implements HTTP request handlers and middleware
// for the SafeRing public API.
package handler

import (
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/go-chi/httprate"
	"go.uber.org/zap"
)

// DefaultRateLimit is the standard rate limit for general API endpoints (100/min per IP).
const DefaultRateLimit = 100

// StrictRateLimit is for high-cost endpoints like model downloads (5/min per IP).
const StrictRateLimit = 5

// ModerateRateLimit is for report submission (20/min per IP).
const ModerateRateLimit = 20

// LowRateLimit is for aggregate data endpoints (10/min per IP).
const LowRateLimit = 10

// CORSHandler returns a CORS middleware handler for the SafeRing API.
func CORSHandler() func(http.Handler) http.Handler {
	return cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Content-Type", "X-Request-ID"},
		ExposedHeaders:   []string{"X-Request-ID", "Retry-After"},
		AllowCredentials: false,
		MaxAge:           300,
	})
}

// RateLimiter returns a middleware that rate-limits requests per IP.
// Uses an in-memory counter; for distributed deployments, use Redis.
func RateLimiter(requests int, window time.Duration) func(http.Handler) http.Handler {
	return httprate.LimitByIP(requests, window)
}

// Logger is a middleware that logs HTTP requests using zap.
// It captures method, path, status code, duration, and client IP.
func Logger(logger *zap.Logger) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()

			// Wrap response writer to capture status code
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)

			defer func() {
				status := ww.Status()
				duration := time.Since(start)
				clientIP := extractClientIP(r)

				// Default to info; error for 5xx, warn for 4xx
				level := zap.InfoLevel
				if status >= 500 {
					level = zap.ErrorLevel
				} else if status >= 400 {
					level = zap.WarnLevel
				}

				logger.Log(level, "HTTP request",
					zap.String("method", r.Method),
					zap.String("path", r.URL.Path),
					zap.Int("status", status),
					zap.Duration("duration", duration),
					zap.String("ip", clientIP),
					zap.String("user_agent", r.UserAgent()),
					zap.Int("bytes", ww.BytesWritten()),
				)
			}()

			next.ServeHTTP(ww, r)
		})
	}
}

// RequestID is a middleware that ensures each request has a unique ID.
// Uses chi's built-in request ID middleware.
func RequestID(next http.Handler) http.Handler {
	return middleware.RequestID(next)
}

// RealIP extracts the real client IP, handling reverse proxy headers.
var RealIP = middleware.RealIP

// Recoverer is a middleware that recovers from panics and logs the error.
func Recoverer(logger *zap.Logger) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					logger.Error("panic recovered",
						zap.Any("panic", rec),
						zap.String("path", r.URL.Path),
						zap.String("method", r.Method),
					)
					http.Error(w, "Internal Server Error", http.StatusInternalServerError)
				}
			}()
			next.ServeHTTP(w, r)
		})
	}
}

// Timeout is a middleware that sets a context timeout for each request.
func Timeout(timeout time.Duration) func(next http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.TimeoutHandler(next, timeout, "{\"error\":\"request timeout\"}")
	}
}

// extractClientIP extracts the client IP from the request.
func extractClientIP(r *http.Request) string {
	// Check X-Forwarded-For header first
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		if ip := net.ParseIP(strings.TrimSpace(parts[0])); ip != nil {
			return ip.String()
		}
	}

	// Check X-Real-IP header
	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		if ip := net.ParseIP(strings.TrimSpace(xri)); ip != nil {
			return ip.String()
		}
	}

	// Fall back to remote address
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// ChainMiddleware is a helper to build middleware chains for specific routes.
// Placeholder for more advanced middleware composition.
func ChainMiddleware(middlewares ...func(http.Handler) http.Handler) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		for i := len(middlewares) - 1; i >= 0; i-- {
			next = middlewares[i](next)
		}
		return next
	}
}
