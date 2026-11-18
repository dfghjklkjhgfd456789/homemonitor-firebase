# HomeMonitor Web Control & Remote Diagnostic Dashboard

A responsive, modular Single-Page Application (SPA) Web Control Dashboard for managing enrolled devices, executing real-time commands, monitoring multi-account Firebase failover pools, and displaying telemetry feeds across dedicated feature tabs.

## 📁 Directory Structure
```
web-dashboard/
├── index.html               # Main Dashboard UI Layout & Tab Navigation Sidebar
├── css/
│   └── style.css            # HomeMonitor UI theme & responsive sidebar styles
├── js/
│   ├── firebase-config.js   # 1 Primary + 5 Fallback Firebase Accounts Failover Pool Config
│   ├── auth.js              # User Login/Signup & Master Admin Role Management
│   ├── tabs.js              # Sidebar Category Expanders & Tab Switcher
│   └── app.js               # Main Dashboard Controller (Commands & Realtime Logs)
└── README.md                # Deployment Guide
```

## 🚀 1-Click Free Deployment Options

### Option 1: Firebase Hosting (Recommended)
1. Install Firebase CLI: `npm install -g firebase-tools`
2. Login to Firebase: `firebase login`
3. Initialize in `web-dashboard/` folder: `firebase init hosting`
4. Deploy: `firebase deploy --only hosting`
5. Access your dashboard at `https://<your-project-id>.web.app`

### Option 2: Vercel / Netlify / GitHub Pages
- Simply upload or push the `web-dashboard/` folder to GitHub, Vercel, or Netlify for free instant HTTPS deployment.

### Option 3: Local Testing
- Simply double-click `index.html` to open directly in Chrome, Firefox, or Edge!
