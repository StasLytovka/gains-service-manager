#!/bin/bash
# deploy.sh — Full install/update script for Gains Service Manager
# Run as: sudo bash deploy.sh
# Prereqs: JDK 17+, Node 18+, nginx installed

set -e
APP_DIR="/opt/gains-service-manager"
BACKEND_JAR="$APP_DIR/backend/gains-service-manager.jar"
FRONTEND_DIST="$APP_DIR/frontend/dist/gains-service-manager-ui/browser"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================================"
echo "  Gains Service Manager — Deploy"
echo "  $(date)"
echo "============================================================"

# ── 1. Prepare directories ──────────────────────────────────────
echo ""
echo "=== Step 1: Directories ==="
mkdir -p "$APP_DIR/backend"
mkdir -p "$APP_DIR/frontend"

# ── 2. Build Spring Boot backend ────────────────────────────────
echo ""
echo "=== Step 2: Build backend (Gradle) ==="
cd "$PROJECT_ROOT/backend"
./gradlew bootJar
cp build/libs/gains-service-manager.jar "$APP_DIR/backend/"
echo "  [OK] JAR copied to $BACKEND_JAR"

# ── 3. Build Angular frontend ────────────────────────────────────
echo ""
echo "=== Step 3: Build frontend (Angular) ==="
cd "$PROJECT_ROOT/frontend"
npm install
npx ng build --configuration production
rm -rf "$APP_DIR/frontend/dist"
cp -r dist "$APP_DIR/frontend/"
echo "  [OK] Frontend built and copied to $FRONTEND_DIST"

# ── 4. Install systemd service ───────────────────────────────────
echo ""
echo "=== Step 4: Install systemd service ==="
cp "$SCRIPT_DIR/gains-manager.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable gains-manager.service
echo "  [OK] gains-manager.service installed"

# ── 5. Sudoers ───────────────────────────────────────────────────
echo ""
echo "=== Step 5: Sudoers ==="
cp "$SCRIPT_DIR/sudoers-gains-manager" /etc/sudoers.d/gains-manager
chmod 440 /etc/sudoers.d/gains-manager
visudo -c -f /etc/sudoers.d/gains-manager && echo "  [OK] sudoers OK" || \
  echo "  [WARN] sudoers syntax error — check /etc/sudoers.d/gains-manager"

# ── 6. shadow group for PAM ──────────────────────────────────────
echo ""
echo "=== Step 6: shadow group for PAM auth ==="
usermod -aG shadow slytovka && echo "  [OK] slytovka added to shadow group" || \
  echo "  [WARN] Could not add to shadow group (may already be there)"

# ── 7. Nginx ─────────────────────────────────────────────────────
echo ""
echo "=== Step 7: Nginx ==="
cp "$SCRIPT_DIR/nginx-gains-manager.conf" /etc/nginx/sites-available/gains-manager
ln -sf /etc/nginx/sites-available/gains-manager /etc/nginx/sites-enabled/gains-manager
nginx -t && systemctl reload nginx && echo "  [OK] nginx reloaded" || \
  echo "  [FAIL] nginx config error — check manually"

# ── 8. Start / restart ───────────────────────────────────────────
echo ""
echo "=== Step 8: Start service ==="
systemctl restart gains-manager.service
sleep 3
systemctl is-active gains-manager.service && echo "  [OK] gains-manager is running" || \
  echo "  [FAIL] service not running — check: journalctl -u gains-manager -n 30"

echo ""
echo "============================================================"
echo "  Deploy complete!"
echo "  URL: http://multitenantpoc.gainsystems.com"
echo "  Backend port: 8090"
echo "  Logs: journalctl -u gains-manager -f"
echo "============================================================"
