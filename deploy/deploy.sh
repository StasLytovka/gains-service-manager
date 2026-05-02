#!/bin/bash
# deploy.sh — Full install/update script for Gains Service Manager
# Run as: sudo bash deploy.sh
set -e

APP_DIR="/opt/gains-service-manager"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================================"
echo "  Gains Service Manager — Deploy"
echo "  $(date)"
echo "============================================================"

# ── 1. Directories ───────────────────────────────────────────────
echo "=== Step 1: Directories ==="
sudo mkdir -p "$APP_DIR/backend"
sudo mkdir -p "$APP_DIR/frontend/dist"

# ── 2. Build backend ─────────────────────────────────────────────
echo "=== Step 2: Build backend (Java + Gradle) ==="
cd "$PROJECT_ROOT/backend"
sh build.sh
sudo cp build/libs/gains-service-manager.jar "$APP_DIR/backend/"
echo "  [OK] JAR deployed"

# ── 3. Build frontend ────────────────────────────────────────────
echo "=== Step 3: Build frontend (Angular) ==="
cd "$PROJECT_ROOT/frontend"
npm install
npx ng build --configuration=production --base-href /svcmgr/
sudo rm -rf "$APP_DIR/frontend/dist/gains-service-manager-ui"
sudo cp -r dist/gains-service-manager-ui "$APP_DIR/frontend/dist/"
echo "  [OK] Frontend deployed"

# ── 4. Restart service ───────────────────────────────────────────
echo "=== Step 4: Restart service ==="
sudo systemctl restart gains-manager.service
sleep 3
sudo systemctl is-active gains-manager.service && \
  echo "  [OK] gains-manager is running" || \
  echo "  [FAIL] check: journalctl -u gains-manager -n 30"

echo "============================================================"
echo "  Deploy complete!"
echo "  URL: https://multitenantpoc.gainsystems.com/svcmgr/"
echo "============================================================"
