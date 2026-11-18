import fs from 'fs';
import path from 'path';

const projectRoot = process.cwd();

const requiredFiles = [
    'extension/shared/constants.js',
    'extension/shared/browser-api.js',
    'extension/shared/configuration.js',
    'extension/shared/origin-validator.js',
    'extension/shared/viewer-detector.js',
    'extension/shared/display-diagnostics.js',
    'extension/shared/coordinate-engine.js',
    'extension/shared/display-geometry-client.js',
    'extension/shared/gesture-engine.js',
    'extension/shared/multitouch-engine.js',
    'extension/shared/pointer-controller.js',
    'extension/shared/input-bridge.js',
    'extension/shared/calibration-engine.js',
    'extension/shared/telemetry-collector.js',
    'extension/shared/message-types.js',
    'extension/content/bootstrap.js',
    'extension/content/precision-controller.js',
    'extension/content/overlay-manager.js',
    'extension/content/viewer-observer.js',
    'extension/content/diagnostics-collector.js',
    'extension/popup/popup.html',
    'extension/popup/popup.css',
    'extension/popup/popup.js',
    'extension/options/options.html',
    'extension/options/options.css',
    'extension/options/options.js',
    'manifests/chromium/manifest.json',
    'manifests/firefox/manifest.json'
];

console.log('Validating extension files...');
let missing = 0;

for (const relPath of requiredFiles) {
    const fullPath = path.join(projectRoot, relPath);
    if (!fs.existsSync(fullPath)) {
        console.error(`❌ Missing file: ${relPath}`);
        missing++;
    }
}

if (missing === 0) {
    console.log('✅ All required extension files are present.');
} else {
    console.error(`❌ Validation failed: ${missing} missing files.`);
    process.exit(1);
}
