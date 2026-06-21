// Command server starts the SafeRing backend HTTP server.
//
// SafeRing provides AI-powered scam call/SMS detection via a privacy-preserving API.
// Zero PII: all phone numbers are stored and transmitted as SHA-256 hashes.
//
// Usage:
//
//	export DATABASE_URL=safering.db
//	export SERVER_PORT=8080
//	go run cmd/server/main.go
//
// Or with Docker:
//
//	docker compose up
package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/go-chi/chi/v5"
	"go.uber.org/zap"

	"github.com/safering/backend/internal/config"
	"github.com/safering/backend/internal/handler"
	"github.com/safering/backend/internal/ml"
	"github.com/safering/backend/internal/scraper"
	"github.com/safering/backend/internal/store"
)

func main() {
	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to load config: %v\n", err)
		os.Exit(1)
	}

	// Initialize logger
	logger, err := newLogger(cfg.Log.Level, cfg.Log.Format)
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to create logger: %v\n", err)
		os.Exit(1)
	}
	defer logger.Sync()

	logger.Info("starting SafeRing backend",
		zap.String("listen", cfg.Server.ListenAddr()),
		zap.String("db_url", maskDSN(cfg.Database.URL)),
	)

	// Context for graceful shutdown
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Initialize database
	db, err := store.NewDB(ctx, cfg.Database, logger)
	if err != nil {
		logger.Fatal("failed to connect to database", zap.Error(err))
	}
	defer db.Close()

	// Run migrations
	if err := db.RunMigrations(ctx); err != nil {
		logger.Fatal("failed to run migrations", zap.Error(err))
	}

	// Initialize stores
	numberStore := store.NewScamNumberStore(db)
	prefixStore := store.NewScamPrefixStore(db)
	reportStore := store.NewScamReportStore(db)
	modelStore := store.NewModelStore(db)

	// Initialize ML pipeline
	mlPipeline := ml.NewPipeline(modelStore, numberStore, reportStore,
		cfg.ML.ModelDir, cfg.ML.TrainInterval, logger)
	mlTrainer := ml.NewTrainer(numberStore, prefixStore, reportStore,
		cfg.ML.ModelDir, logger)

	// Initialize scraper aggregator
	aggregator := scraper.NewAggregator(numberStore, prefixStore,
		cfg.Scraper.Interval, logger)

	// Register scrapers based on config
	if cfg.Scraper.FTCEnabled {
		aggregator.Register(scraper.NewFTCConsumerSentinelScraper(cfg.Scraper.FTCApiKey, logger))
	}
	if cfg.Scraper.BBBEnabled {
		aggregator.Register(scraper.NewBBBScamTrackerScraper(logger))
	}
	if cfg.Scraper.RedditEnabled {
		aggregator.Register(scraper.NewRedditScraper(
			cfg.Scraper.RedditClientID,
			cfg.Scraper.RedditClientSecret,
			cfg.Scraper.RedditUserAgent,
			logger,
		))
	}

	// Register X/Twitter scraper if bearer token is configured
	if cfg.Scraper.TwitterBearerToken != "" {
		aggregator.Register(scraper.NewTwitterScraper(
			cfg.Scraper.TwitterBearerToken,
			logger,
		))
	}

	// Start background tasks
	go aggregator.StartBackground(ctx)
	go mlTrainer.StartTrainingLoop(ctx, cfg.ML.TrainInterval)

	// Initialize handlers
	checkHandler := handler.NewCheckHandler(numberStore, logger)
	prefixesHandler := handler.NewPrefixesHandler(prefixStore, logger)
	modelHandler := handler.NewModelHandler(mlPipeline, logger)
	reportHandler := handler.NewReportHandler(reportStore, logger)
	statsHandler := handler.NewStatsHandler(reportStore, numberStore, prefixStore, aggregator, logger)

	// Build router
	r := chi.NewRouter()

	// Global middleware
	r.Use(handler.RequestID)
	r.Use(handler.RealIP)
	r.Use(handler.Logger(logger))
	r.Use(handler.Recoverer(logger))
	r.Use(handler.CORSHandler())
	r.Use(handler.Timeout(cfg.Server.ReadTimeout))

	// Health check
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		if err := db.HealthCheck(r.Context()); err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			w.Write([]byte(`{"status":"unhealthy"}`))
			return
		}
		w.Write([]byte(`{"status":"ok"}`))
	})

	// Initialize scraper coordinator (distributed agent management)
	coordinator := scraper.NewCoordinator(numberStore, prefixStore, aggregator, logger)

	// API v1 routes
	r.Route("/v1", func(r chi.Router) {
		// GET /v1/check?hash={sha256} — Check if a number is a known scam
		// Rate limit: 100/min per IP
		r.With(handler.RateLimiter(handler.DefaultRateLimit, cfg.Rate.Window)).
			Get("/check", checkHandler.ServeHTTP)

		// GET /v1/prefixes — Get known scam prefixes for local caching
		// Rate limit: 10/min per IP
		r.With(handler.RateLimiter(handler.LowRateLimit, cfg.Rate.Window)).
			Get("/prefixes", prefixesHandler.ServeHTTP)

		// GET /v1/model — Get model info
		// GET /v1/model/latest — Get latest model version
		// Rate limit: 5/min per IP
		r.With(handler.RateLimiter(handler.StrictRateLimit, cfg.Rate.Window)).
			Get("/model", modelHandler.ServeHTTP)
		r.With(handler.RateLimiter(handler.StrictRateLimit, cfg.Rate.Window)).
			Get("/model/latest", modelHandler.ServeHTTP)

		// POST /v1/report — Submit a scam report (hash only)
		// Rate limit: 20/min per IP
		r.With(handler.RateLimiter(handler.ModerateRateLimit, cfg.Rate.Window)).
			Post("/report", reportHandler.ServeHTTP)

		// GET /v1/stats — Anonymous aggregate statistics
		// Rate limit: 10/min per IP
		r.With(handler.RateLimiter(handler.LowRateLimit, cfg.Rate.Window)).
			Get("/stats", statsHandler.ServeHTTP)

		// Agent coordination endpoints (for distributed scraper agents)
		// No rate limiting — agents are internal (Tailscale)
		r.Route("/agent", coordinator.Routes)
	})

	// Create HTTP server
	srv := &http.Server{
		Addr:         cfg.Server.ListenAddr(),
		Handler:      r,
		ReadTimeout:  cfg.Server.ReadTimeout,
		WriteTimeout: cfg.Server.WriteTimeout,
		IdleTimeout:  cfg.Server.IdleTimeout,
	}

	// Start server in a goroutine
	go func() {
		logger.Info("HTTP server listening", zap.String("addr", cfg.Server.ListenAddr()))
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("HTTP server error", zap.Error(err))
		}
	}()

	// Wait for shutdown signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	sig := <-quit

	logger.Info("shutting down server", zap.String("signal", sig.String()))

	// Graceful shutdown with timeout
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), cfg.Server.ShutdownTimeout)
	defer shutdownCancel()

	cancel() // Cancel background tasks

	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("server shutdown error", zap.Error(err))
	}

	logger.Info("server stopped")
}

// newLogger creates a zap logger with the specified level and format.
func newLogger(level, format string) (*zap.Logger, error) {
	var lvl zap.AtomicLevel
	switch level {
	case "debug":
		lvl = zap.NewAtomicLevelAt(zap.DebugLevel)
	case "warn":
		lvl = zap.NewAtomicLevelAt(zap.WarnLevel)
	case "error":
		lvl = zap.NewAtomicLevelAt(zap.ErrorLevel)
	default:
		lvl = zap.NewAtomicLevelAt(zap.InfoLevel)
	}

	var cfg zap.Config
	if format == "json" {
		cfg = zap.NewProductionConfig()
	} else {
		cfg = zap.NewDevelopmentConfig()
	}
	cfg.Level = lvl
	cfg.DisableCaller = true

	return cfg.Build()
}

// maskDSN masks the password/credentials in a database connection string for logging.
func maskDSN(dsn string) string {
	if len(dsn) <= 10 {
		return dsn
	}
	return dsn[:10] + "..."
}
