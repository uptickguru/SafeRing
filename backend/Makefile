# SafeRing Backend — Service Management
# "You idle, we work you" — low-pressure distributed scraper.

SERVICE_DIR ?= ~/.config/systemd/user
BIN_DIR     ?= $(CURDIR)
SERVER_PORT ?= 9093

.PHONY: all build install uninstall start stop restart status

all: build

# --- Build ---
build:
	go build -o safering-server ./cmd/server/
	go build -o scraper-agent ./cmd/scraper-agent/

build-server:
	go build -o safering-server ./cmd/server/

build-agent:
	go build -o scraper-agent ./cmd/scraper-agent/

# --- Install systemd user services ---
install: build $(SERVICE_DIR)
	@echo "Installing systemd user services..."
	cp safering.service $(SERVICE_DIR)/safering-backend.service
	cp scraper-agent.service $(SERVICE_DIR)/safering-scraper.service
	systemctl --user daemon-reload
	@echo ""
	@echo "To enable and start:"
	@echo "  systemctl --user enable --now safering-backend"
	@echo "  systemctl --user enable --now safering-scraper"
	@echo ""
	@echo "To view logs:"
	@echo "  journalctl --user -fu safering-backend"
	@echo "  journalctl --user -fu safering-scraper"

$(SERVICE_DIR):
	mkdir -p $(SERVICE_DIR)

uninstall:
	-systemctl --user stop safering-backend safering-scraper 2>/dev/null
	-systemctl --user disable safering-backend safering-scraper 2>/dev/null
	rm -f $(SERVICE_DIR)/safering-backend.service
	rm -f $(SERVICE_DIR)/safering-scraper.service
	systemctl --user daemon-reload

# --- Manage services ---
start:
	systemctl --user start safering-backend

stop:
	systemctl --user stop safering-backend

restart:
	systemctl --user restart safering-backend

status:
	@echo "=== Backend ==="
	-systemctl --user status safering-backend 2>&1 | head -15
	@echo ""
	@echo "=== Scraper Agent ==="
	-systemctl --user status safering-scraper 2>&1 | head -15

logs:
	journalctl --user -fu safering-backend -n 20

logs-agent:
	journalctl --user -fu safering-scraper -n 20

# --- Deploy scraper-agent to remote host (e.g., R730) ---
# Usage: make deploy-agent HOST=r730-core
deploy-agent: build-agent
	@if [ -z "$(HOST)" ]; then echo "Usage: make deploy-agent HOST=r730-core"; exit 1; fi
	scp scraper-agent $(HOST):~
	ssh $(HOST) "sudo mkdir -p /opt/safering && sudo mv ~/scraper-agent /opt/safering/ && sudo cp -f scraper-agent.service /etc/systemd/system/ && sudo systemctl daemon-reload && sudo systemctl enable --now safering-scraper"

# --- Quick test ---
test-server:
	@echo "Testing backend health..."
	curl -sf http://localhost:$(SERVER_PORT)/health && echo " OK" || echo " FAILED"
	@echo "Testing agent endpoints..."
	curl -sf http://localhost:$(SERVER_PORT)/v1/agent/stats | head -c 200 && echo ""
	@echo "Testing check endpoint..."
	curl -sf "http://localhost:$(SERVER_PORT)/v1/check?hash=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" | head -c 200

# --- Clean ---
clean:
	rm -f safering-server scraper-agent

# --- Docker Tor (no sudo needed) ---
tor-start:
	docker start tor-socks 2>/dev/null || docker run -d --name tor-socks \
		--restart unless-stopped \
		-p 127.0.0.1:9050:9050 \
		dperson/torproxy:latest
	@echo "Tor SOCKS5 running on 127.0.0.1:9050"

tor-stop:
	docker stop tor-socks 2>/dev/null; true

tor-logs:
	docker logs -f tor-socks
