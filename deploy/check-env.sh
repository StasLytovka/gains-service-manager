#!/bin/bash
# check-env.sh — verifies all required packages and configs are in place
# Run as: bash deploy/check-env.sh

OK="\e[32m[OK]\e[0m"
FAIL="\e[31m[FAIL]\e[0m"
WARN="\e[33m[WARN]\e[0m"

echo "============================================================"
echo "  Gains Service Manager — Environment Check"
echo "  $(date)"
echo "============================================================"

# ── Java ─────────────────────────────────────────────────────────
echo ""
echo "=== Java ==="
if javac --version 2>/dev/null | grep -q "17"; then
    echo -e "$OK javac 17 found: $(javac --version 2>&1)"
else
    echo -e "$FAIL javac 17 not found. Run: sudo apt install openjdk-17-jdk"
fi

if java --version 2>/dev/null | grep -q "17"; then
    echo -e "$OK java 17 found: $(java --version 2>&1 | head -1)"
else
    echo -e "$FAIL java 17 not found"
fi

# ── Gradle ───────────────────────────────────────────────────────
echo ""
echo "=== Gradle ==="
if [ -f "$HOME/gains-mgr-java/backend/gradlew" ]; then
    echo -e "$OK gradlew found"
else
    echo -e "$FAIL gradlew not found. Run: cd ~/gains-mgr-java/backend && gradle wrapper --gradle-version 8.7"
fi

if [ -f "$HOME/.gradle/gradle.properties" ]; then
    echo -e "$OK gradle.properties found: $(cat ~/.gradle/gradle.properties)"
else
    echo -e "$WARN gradle.properties missing. Run: echo 'org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64' > ~/.gradle/gradle.properties"
fi

# ── Node.js / npm ────────────────────────────────────────────────
echo ""
echo "=== Node.js / npm ==="
if node --version 2>/dev/null | grep -qE "v1[89]|v2[0-9]"; then
    echo -e "$OK node found: $(node --version)"
else
    echo -e "$FAIL node 18+ not found. Run: curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt install nodejs"
fi

if npm --version 2>/dev/null; then
    echo -e "$OK npm found: $(npm --version)"
else
    echo -e "$FAIL npm not found"
fi

if [ -d "$HOME/gains-mgr-java/frontend/node_modules" ]; then
    echo -e "$OK node_modules present"
else
    echo -e "$WARN node_modules missing. Run: cd ~/gains-mgr-java/frontend && npm install"
fi

# ── nginx ────────────────────────────────────────────────────────
echo ""
echo "=== nginx ==="
if systemctl is-active nginx &>/dev/null; then
    echo -e "$OK nginx is running"
else
    echo -e "$FAIL nginx is not running"
fi

if [ -f /etc/nginx/sites-enabled/gains-manager ]; then
    echo -e "$WARN gains-manager site enabled separately (should be inside multitenantpoc)"
fi

if grep -q "svcmgr" /etc/nginx/sites-available/multitenantpoc 2>/dev/null; then
    echo -e "$OK /svcmgr/ location found in multitenantpoc nginx config"
else
    echo -e "$FAIL /svcmgr/ not configured in nginx"
fi

# ── Frontend dist ────────────────────────────────────────────────
echo ""
echo "=== Frontend ==="
if [ -f /var/www/svcmgr/index.html ]; then
    echo -e "$OK /var/www/svcmgr/index.html exists"
else
    echo -e "$FAIL /var/www/svcmgr/ missing or empty"
fi

if [ -L /var/www/svcmgr ]; then
    echo -e "$OK /var/www/svcmgr is a symlink → $(readlink /var/www/svcmgr)"
else
    echo -e "$WARN /var/www/svcmgr is not a symlink"
fi

# ── Backend JAR ──────────────────────────────────────────────────
echo ""
echo "=== Backend ==="
if [ -f /opt/gains-service-manager/backend/gains-service-manager.jar ]; then
    echo -e "$OK JAR found: $(ls -lh /opt/gains-service-manager/backend/gains-service-manager.jar | awk '{print $5, $6, $7, $8}')"
else
    echo -e "$FAIL JAR not found at /opt/gains-service-manager/backend/"
fi

# ── systemd service ──────────────────────────────────────────────
echo ""
echo "=== gains-manager service ==="
if systemctl is-active gains-manager &>/dev/null; then
    echo -e "$OK gains-manager is running"
    echo "    Port 8090: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8090/api/auth/login -X POST -H 'Content-Type: application/json' -d '{}' 2>/dev/null)"
else
    echo -e "$FAIL gains-manager is NOT running. Run: sudo systemctl start gains-manager"
fi

if systemctl is-enabled gains-manager &>/dev/null; then
    echo -e "$OK gains-manager is enabled (auto-start on boot)"
else
    echo -e "$WARN gains-manager is not enabled. Run: sudo systemctl enable gains-manager"
fi

# ── PAM / shadow group ───────────────────────────────────────────
echo ""
echo "=== PAM / shadow ==="
if id slytovka 2>/dev/null | grep -q shadow; then
    echo -e "$OK slytovka is in shadow group"
else
    echo -e "$WARN slytovka not in shadow group. Run: sudo usermod -aG shadow slytovka"
fi

# ── sudoers ──────────────────────────────────────────────────────
echo ""
echo "=== sudoers ==="
if [ -f /etc/sudoers.d/gains-manager ]; then
    echo -e "$OK /etc/sudoers.d/gains-manager exists"
    sudo visudo -c -f /etc/sudoers.d/gains-manager 2>/dev/null && \
        echo -e "$OK sudoers syntax OK" || \
        echo -e "$FAIL sudoers syntax error"
else
    echo -e "$FAIL sudoers file missing. Run: sudo cp deploy/sudoers-gains-manager /etc/sudoers.d/gains-manager"
fi

# ── Service discovery ────────────────────────────────────────────
echo ""
echo "=== Service discovery ==="
COUNT=$(sudo find /home -path "*/.config/systemd/user/Gains8GuiServer*.service" \
    -not -path "*/default.target.wants/*" 2>/dev/null | wc -l)
echo -e "$OK Found $COUNT Gains service files"

echo ""
echo "============================================================"
echo "  Check complete"
echo "============================================================"
