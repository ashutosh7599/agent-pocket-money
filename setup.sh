#!/bin/bash
set -e

echo "🚀 Starting project local infrastructure setup..."

# 1. Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running or not installed. Please start Docker and try again."
    exit 1
fi

# 2. Spin up infrastructure containers
echo "📦 Spinning up Docker containers (Postgres, Redis, Kafka, Cosmos DB, OpenTelemetry Collector)..."
docker compose up -d

echo "⏳ Waiting for databases to initialize..."
sleep 10

echo "✅ Setup complete! All infrastructure services are running."
echo "   - PostgreSQL: localhost:5432"
echo "   - Redis: localhost:6379"
echo "   - Kafka: localhost:9092"
echo "   - Cosmos DB Emulator: https://localhost:8081"
echo "   - OpenTelemetry Collector (OTLP): localhost:4318"
