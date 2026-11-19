#!/usr/bin/env bash
# start-admin-app.sh — HomeMonitor Linux Master Admin App Launcher

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

echo "============================================================"
echo "🛡️ Starting HomeMonitor Master Admin Linux Server App..."
echo "============================================================"

if command -v node &> /dev/null && [ -f "$DIR/node_modules/sqlite3/package.json" ]; then
    echo "⚡ Launching Node.js Admin Server..."
    node server.js
elif command -v python3 &> /dev/null; then
    echo "🐍 Launching Python Zero-Dependency Admin Server..."
    python3 server.py
else
    echo "❌ Error: Neither Node.js nor Python 3 is installed."
    exit 1
fi
