#!/usr/bin/env bash
# Levanta el sistema limpio, espera, y corre la suite BDD. Los reportes quedan en e2e-tests/reports.
set -u
cd "$(dirname "$0")/.."
docker compose -f docker-compose.yml down -v || true
docker compose -f docker-compose.yml up -d --build
./scripts/wait-for-healthy.sh "${BASE_URL:-http://localhost:8080}" 240 || exit 1
cd e2e-tests
npm ci
npx cucumber-js || true