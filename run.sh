#!/bin/bash
set -e

echo "🚀 Starting application..."
docker compose up -d postgres otel-collector
mvn spring-boot:run