// Package config provides environment-based configuration for the SafeRing backend.
// All configuration is loaded from environment variables with sensible defaults.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config holds all configuration for the SafeRing backend server.
type Config struct {
	Server   ServerConfig
	Database DatabaseConfig
	Scraper  ScraperConfig
	Rate     RateLimitConfig
	Log      LogConfig
	ML       MLConfig
	Redis    RedisConfig
}

// ServerConfig holds HTTP server-related configuration.
type ServerConfig struct {
	Host              string
	Port              int
	ReadTimeout       time.Duration
	WriteTimeout      time.Duration
	IdleTimeout       time.Duration
	ShutdownTimeout   time.Duration
	TrustedProxies    []string
}

// DatabaseConfig holds database connection configuration.
type DatabaseConfig struct {
	URL            string
	MaxOpenConns   int
	MaxIdleConns   int
	ConnMaxLifetime time.Duration
}

// ScraperConfig holds scraper pipeline configuration.
type ScraperConfig struct {
	Interval         time.Duration
	FTCEnabled       bool
	FTCApiKey        string
	BBBEnabled       bool
	RedditEnabled    bool
	RedditClientID       string
	RedditClientSecret   string
	RedditUserAgent  string
	TwitterBearerToken   string
}

// RateLimitConfig holds rate limiting configuration.
type RateLimitConfig struct {
	RequestsPerIP int
	Window        time.Duration
}

// LogConfig holds logging configuration.
type LogConfig struct {
	Level  string
	Format string
}

// MLConfig holds machine learning pipeline configuration.
type MLConfig struct {
	ModelDir      string
	TrainInterval time.Duration
}

// RedisConfig holds optional Redis configuration.
type RedisConfig struct {
	URL string
}

// Load reads configuration from environment variables.
// Returns a Config with defaults applied for any unset variables.
func Load() (*Config, error) {
	cfg := &Config{
		Server: ServerConfig{
			Host:            getEnv("SERVER_HOST", "0.0.0.0"),
			Port:            getEnvInt("SERVER_PORT", 8080),
			ReadTimeout:     getEnvDuration("SERVER_TIMEOUT", 30*time.Second),
			WriteTimeout:    getEnvDuration("SERVER_TIMEOUT", 30*time.Second),
			IdleTimeout:     120 * time.Second,
			ShutdownTimeout: 15 * time.Second,
			TrustedProxies:  getEnvSlice("TRUSTED_PROXIES", nil),
		},
		Database: DatabaseConfig{
			URL:             getEnv("DATABASE_URL", "safering.db"),
			MaxOpenConns:    getEnvInt("DB_MAX_OPEN", 25),
			MaxIdleConns:    getEnvInt("DB_MAX_IDLE", 5),
			ConnMaxLifetime: 5 * time.Minute,
		},
		Scraper: ScraperConfig{
			Interval:           getEnvDuration("SCRAPER_INTERVAL", 6*time.Hour),
			FTCEnabled:         getEnvBool("SCRAPER_FTC_ENABLED", true),
			FTCApiKey:           getEnv("SCRAPER_FTC_API_KEY", "DEMO_KEY"),
			BBBEnabled:         getEnvBool("SCRAPER_BBB_ENABLED", true),
			RedditEnabled:      getEnvBool("SCRAPER_REDDIT_ENABLED", true),
			RedditClientID:       getEnv("SCRAPER_REDDIT_CLIENT_ID", ""),
			RedditClientSecret:   getEnv("SCRAPER_REDDIT_CLIENT_SECRET", ""),
			RedditUserAgent:      getEnv("SCRAPER_REDDIT_USER_AGENT", "SafeRing/1.0"),
			TwitterBearerToken:   getEnv("SCRAPER_TWITTER_BEARER_TOKEN", ""),
		},
		Rate: RateLimitConfig{
			RequestsPerIP: getEnvInt("RATE_LIMIT_PER_IP", 100),
			Window:        getEnvDuration("RATE_LIMIT_WINDOW", 1*time.Minute),
		},
		Log: LogConfig{
			Level:  getEnv("LOG_LEVEL", "info"),
			Format: getEnv("LOG_FORMAT", "console"),
		},
		ML: MLConfig{
			ModelDir:      getEnv("ML_MODEL_DIR", "./data/models"),
			TrainInterval: getEnvDuration("ML_TRAIN_INTERVAL", 24*time.Hour),
		},
		Redis: RedisConfig{
			URL: getEnv("REDIS_URL", ""),
		},
	}

	if err := cfg.validate(); err != nil {
		return nil, fmt.Errorf("config validation: %w", err)
	}

	return cfg, nil
}

// validate checks the configuration for obvious errors.
func (c *Config) validate() error {
	if c.Server.Port < 1 || c.Server.Port > 65535 {
		return fmt.Errorf("invalid server port: %d", c.Server.Port)
	}
	if c.Database.URL == "" {
		return fmt.Errorf("DATABASE_URL must not be empty")
	}
	if c.Scraper.Interval < time.Minute {
		return fmt.Errorf("SCRAPER_INTERVAL must be at least 1m")
	}
	if c.Rate.RequestsPerIP < 1 {
		return fmt.Errorf("RATE_LIMIT_PER_IP must be >= 1")
	}
	return nil
}

// ListenAddr returns the formatted address string for the HTTP server.
func (s ServerConfig) ListenAddr() string {
	return fmt.Sprintf("%s:%d", s.Host, s.Port)
}

// IsSQLite returns true if the database URL points to a SQLite file.
func (d DatabaseConfig) IsSQLite() bool {
	return !strings.HasPrefix(d.URL, "postgres://") &&
		!strings.HasPrefix(d.URL, "postgresql://")
}

// getEnv returns the value of an environment variable or a default.
func getEnv(key, defaultVal string) string {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		return val
	}
	return defaultVal
}

// getEnvInt returns the integer value of an environment variable or a default.
func getEnvInt(key string, defaultVal int) int {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		if i, err := strconv.Atoi(val); err == nil {
			return i
		}
	}
	return defaultVal
}

// getEnvBool returns the boolean value of an environment variable or a default.
func getEnvBool(key string, defaultVal bool) bool {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		if b, err := strconv.ParseBool(val); err == nil {
			return b
		}
	}
	return defaultVal
}

// getEnvDuration returns the duration value of an environment variable or a default.
func getEnvDuration(key string, defaultVal time.Duration) time.Duration {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		if d, err := time.ParseDuration(val); err == nil {
			return d
		}
	}
	return defaultVal
}

// getEnvSlice returns a slice from a comma-separated env variable or a default.
func getEnvSlice(key string, defaultVal []string) []string {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		parts := strings.Split(val, ",")
		for i := range parts {
			parts[i] = strings.TrimSpace(parts[i])
		}
		return parts
	}
	return defaultVal
}
