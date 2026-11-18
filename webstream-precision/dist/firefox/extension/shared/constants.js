/**
 * WebStream Precision Control - Shared Constants
 */

export const DEFAULT_ORIGINS = [
    'http://127.0.0.1:5467',
    'http://localhost:5467'
];

export const ALLOWED_DOMAIN_SUFFIXES = [
    'localhost',
    '127.0.0.1',
    'trycloudflare.com',
    'abishekyadav157.com.np',
    'workers.dev'
];

export const VIEWER_SELECTOR = 'img#stream';
export const STREAM_PATH_KEYWORD = '/stream';

export const STORAGE_KEYS = {
    APPROVED_ORIGINS: 'webstream_approved_origins',
    OVERLAY_ENABLED: 'webstream_overlay_enabled',
    DIAGNOSTICS_ENABLED: 'webstream_diagnostics_enabled'
};

export const DEFAULT_SETTINGS = {
    approvedOrigins: DEFAULT_ORIGINS,
    overlayEnabled: true,
    diagnosticsEnabled: true
};
