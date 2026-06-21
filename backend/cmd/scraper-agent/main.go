// Command scraper-agent is a distributed scraping worker for SafeRing.
//
// It runs on any machine with internet access and connects to the SafeRing
// coordinator over Tailscale (or any network). The agent constantly probes
// data sources for new scam phone numbers, respects rate limits, and reports
// findings back.
//
// Usage:
//
//	# Run on this machine, connecting to coordinator
//	scraper-agent --coordinator http://localhost:9090/v1/agent \
//	              --agent-id "$(hostname)-01"
//
//	# Run on R730, connecting over Tailscale
//	scraper-agent --coordinator http://web-hosting-portal:9090/v1/agent \
//	              --agent-id "r730-01" \
//	              --sources "ftc-gov,bbb-scamtracker,reddit-r-scams"
//
// The agent runs forever until SIGINT/SIGTERM. It handles:
//   - Heartbeat + registration with coordinator
//   - Task assignment and execution
//   - Autonomous scraping when coordinator is unreachable
//   - Smart throttle management (token buckets, backoff, Retry-After)
//   - Telemetry reporting
package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"go.uber.org/zap"

	"github.com/safering/backend/internal/scraper"
)

func main() {
	var (
		coordinatorURL = flag.String("coordinator", "http://localhost:9093",
			"Coordinator API base URL (without path suffix)")
		agentID = flag.String("agent-id", "",
			"Unique agent identifier (default: hostname)")
		sourceFilter = flag.String("sources", "",
			"Comma-separated sources to scrape (empty = all enabled)")
		socks5Proxy = flag.String("socks5-proxy", os.Getenv("SOCKS5_PROXY"),
			"SOCKS5 proxy address (e.g. '127.0.0.1:9050'). Also reads SOCKS5_PROXY env var.")
		disableLoadMon = flag.Bool("no-loadmon", false,
			"Disable resource-aware load monitoring (always scrape at full speed)")
		logLevel = flag.String("log-level", "info",
			"Log level (debug, info, warn, error)")
		version = flag.Bool("version", false,
			"Print version and exit")
	)
	flag.Parse()

	if *version {
		fmt.Println("scraper-agent v0.1.0")
		return
	}

	// Pick agent ID
	if *agentID == "" {
		hostname, err := os.Hostname()
		if err != nil {
			hostname = "unknown"
		}
		id := hostname + "-" + os.Getenv("USER")
		agentID = &id
	}

	// Logger
	zapCfg := zap.NewDevelopmentConfig()
	if *logLevel == "info" {
		zapCfg.Level = zap.NewAtomicLevelAt(zap.InfoLevel)
	} else if *logLevel == "debug" {
		zapCfg.Level = zap.NewAtomicLevelAt(zap.DebugLevel)
	}
	logger, err := zapCfg.Build()
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to create logger: %v\n", err)
		os.Exit(1)
	}
	defer logger.Sync()

	logger.Info("starting scraper agent",
		zap.String("agent_id", *agentID),
		zap.String("coordinator", *coordinatorURL),
	)

	// Create agent
	agent := scraper.NewDistributedAgent(scraper.DistributedAgentConfig{
		CoordinatorURL: *coordinatorURL,
		AgentID:        *agentID,
		Logger:         logger,
		Version:        "0.1.0",
		Socks5Proxy:         *socks5Proxy,
		EnableLoadMonitor:   !*disableLoadMon,
	})

	// Filter sources if specified (disable all except the listed ones)
	if *sourceFilter != "" {
		agent.Sources = scraper.NewDefaultRegistry()
		selected := strings.Split(*sourceFilter, ",")
		for _, s := range agent.Sources.Sources() {
			found := false
			for _, sel := range selected {
				if strings.TrimSpace(sel) == s.Name {
					found = true
					break
				}
			}
			agent.Sources.Enable(s.Name, found)
		}
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Handle shutdown signals
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		sig := <-sigCh
		logger.Info("shutting down", zap.String("signal", sig.String()))
		cancel()
	}()

	// Run forever
	if err := agent.Run(ctx); err != nil && err != context.Canceled {
		logger.Fatal("agent error", zap.Error(err))
	}

	logger.Info("agent stopped")
}
