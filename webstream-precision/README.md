# WebStream Precision Control Extension (Foundation E2)

**Version:** 1.0.0  
**Target Environments:** Chrome, Chromium, Brave, Firefox (Manifest V3)

---

## Overview

WebStream Precision Control is a cross-browser extension providing high-precision viewport diagnostics, visible viewer detection, and non-interactive overlay visualization for the HomeMonitor WebStream remote control system.

---

## Key Features

- **Strict Origin Validation:** Validates exact local (`http://127.0.0.1:5467`) and configured remote HTTPS tunnel origins with domain boundary checking. Zero substring matching.
- **Viewer Detector:** Automatically detects the existing `<img id="stream" src="/stream">` element and extracts intrinsic/rendered dimensions and device pixel ratio.
- **Non-Interactive Precision Overlay:** Renders a visual bounding frame around the active WebStream screen without intercepting clicks or competing with existing input handlers.
- **Zero Secret Storage:** Does not access or store session cookies (`session_id`), master passcodes, or CSRF tokens in extension storage.
- **Cross-Browser Compatibility:** Supports Chromium-based browsers (Chrome, Brave) and Firefox using a unified JavaScript core and distinct MV3 manifests.

---

## Build & Packaging

To validate and build the extension ZIP packages:

```bash
cd webstream-precision
npm test
npm run build
```

Build outputs generated in `dist/`:
- `dist/webstream-precision-chromium.zip`
- `dist/webstream-precision-firefox.zip`

---

## Browser Installation Instructions

### Google Chrome / Chromium / Brave
1. Open `chrome://extensions/` (or `brave://extensions/`).
2. Enable **Developer mode** (toggle in upper right).
3. Click **Load unpacked** and select the `dist/chromium/` directory (or extract `dist/webstream-precision-chromium.zip`).

### Mozilla Firefox
1. Open `about:debugging#/runtime/this-firefox`.
2. Click **Load Temporary Add-on...**
3. Select `dist/firefox/manifest.json` (or extract `dist/webstream-precision-firefox.zip`).
