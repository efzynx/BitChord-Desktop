# Multi-stage build for cross-platform support (AMD64 & ARM64)
FROM golang:1.23-alpine AS builder

WORKDIR /app

# Copy dependency specifications for caching
COPY go.mod go.sum ./
RUN sed -i 's/go 1.27.0/go 1.22/g' go.mod 2>/dev/null || true
RUN go mod download

# Copy source code and build standalone static binary
COPY . .
RUN sed -i 's/go 1.27.0/go 1.22/g' go.mod 2>/dev/null || true
RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o party-server .

# Minimal runtime image based on Alpine Linux (~15 MB)
FROM alpine:latest

RUN apk --no-cache add ca-certificates tzdata

WORKDIR /app
COPY --from=builder /app/party-server .

ENV PORT=8000
ENV JAM_TRUST_PROXY=true
ENV JAM_MAX_MEMBERS=10
ENV JAM_MAX_PARTIES=200

EXPOSE 8000

CMD ["./party-server"]
