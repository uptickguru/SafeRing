# SafeRing Backend — Multi-stage Docker Build
#
# Stage 1: Build Go binary
FROM golang:1.22-alpine AS builder

RUN apk add --no-cache gcc musl-dev sqlite-dev

WORKDIR /app

# Cache module dependencies
COPY go.mod go.sum ./
RUN go mod download

# Copy source and build
COPY . .
RUN CGO_ENABLED=1 GOOS=linux go build -ldflags="-s -w" -o /app/server ./cmd/server/

# Stage 2: Minimal runtime image
FROM alpine:3.19

RUN apk add --no-cache ca-certificates tzdata sqlite-libs

# Create non-root user
RUN adduser -D -h /app safering
USER safering

WORKDIR /app

# Copy binary and data directories
COPY --from=builder /app/server .
COPY --from=builder /app/internal/store/migrations ./internal/store/migrations/

# Create directories for runtime data
RUN mkdir -p /app/data/models

# Expose API port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["./server"]
