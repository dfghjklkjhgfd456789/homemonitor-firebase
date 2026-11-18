/**
 * WebStream Precision Control - Origin Validator
 * Strictly validates WebStream origins using exact URI parsing and domain boundary matching.
 * Rejects substring matching, userinfo credentials, and non-HTTP/HTTPS schemes.
 */

import { ALLOWED_DOMAIN_SUFFIXES } from './constants.js';

export function isHostAllowed(hostname) {
    if (!hostname || typeof hostname !== 'string') return false;
    const lower = hostname.toLowerCase().replace(/\.$/, '');
    if (!lower) return false;

    for (const allowed of ALLOWED_DOMAIN_SUFFIXES) {
        if (lower === allowed || lower.endsWith('.' + allowed)) {
            return true;
        }
    }
    return false;
}

export function validateOrigin(urlString) {
    if (!urlString || typeof urlString !== 'string') {
        return { valid: false, reason: 'URL string is empty or invalid' };
    }

    const trimmed = urlString.trim();

    try {
        const parsed = new URL(trimmed);

        // Reject userinfo credentials
        if (parsed.username || parsed.password) {
            return { valid: false, reason: 'URL must not contain credentials' };
        }

        // Scheme validation
        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
            return { valid: false, reason: `Unsupported scheme '${parsed.protocol}'` };
        }

        const hostname = parsed.hostname;
        if (!isHostAllowed(hostname)) {
            return { valid: false, reason: `Host '${hostname}' is not in approved WebStream allowlist` };
        }

        const normalizedOrigin = `${parsed.protocol}//${parsed.host}`;

        return {
            valid: true,
            protocol: parsed.protocol,
            hostname: parsed.hostname,
            port: parsed.port,
            normalizedOrigin
        };
    } catch (e) {
        return { valid: false, reason: `Malformed URL: ${e.message}` };
    }
}

export function isOriginApproved(targetUrl, approvedOriginsList = []) {
    const val = validateOrigin(targetUrl);
    if (!val.valid) return false;

    if (!Array.isArray(approvedOriginsList) || approvedOriginsList.length === 0) {
        return isHostAllowed(val.hostname);
    }

    return approvedOriginsList.some(approved => {
        const appVal = validateOrigin(approved);
        return appVal.valid && appVal.normalizedOrigin === val.normalizedOrigin;
    });
}
