/**
 * WebStream Precision Control - Extension Display Geometry Client (E4)
 * Retrieves and validates Android display geometry metadata through ambient authenticated same-origin flow.
 * Does NOT store session tokens, passcodes, or credentials.
 */

export function validateGeometrySchema(data) {
    if (!data || typeof data !== 'object') {
        return { valid: false, reason: 'Geometry metadata is missing or null' };
    }

    if (data.isValid === false) {
        return { valid: false, reason: 'Android display geometry reported as invalid' };
    }

    const { screenshotWidth, screenshotHeight, inputWidth, inputHeight, geometryVersion } = data;

    if (typeof screenshotWidth !== 'number' || screenshotWidth <= 0 ||
        typeof screenshotHeight !== 'number' || screenshotHeight <= 0) {
        return { valid: false, reason: 'Invalid screenshot dimensions in metadata' };
    }

    if (typeof inputWidth !== 'number' || inputWidth <= 0 ||
        typeof inputHeight !== 'number' || inputHeight <= 0) {
        return { valid: false, reason: 'Invalid input coordinate dimensions in metadata' };
    }

    if (typeof geometryVersion !== 'number' || geometryVersion <= 0) {
        return { valid: false, reason: 'Invalid or missing geometryVersion' };
    }

    return { valid: true, reason: null };
}

export function compareGeometryWithE3Intrinsic(androidGeometry, e3NaturalWidth, e3NaturalHeight) {
    const val = validateGeometrySchema(androidGeometry);
    if (!val.valid) return { matched: false, reason: val.reason };

    if (!e3NaturalWidth || !e3NaturalHeight || e3NaturalWidth <= 0 || e3NaturalHeight <= 0) {
        return { matched: false, reason: 'Invalid E3 intrinsic image dimensions' };
    }

    const widthMatch = androidGeometry.screenshotWidth === e3NaturalWidth;
    const heightMatch = androidGeometry.screenshotHeight === e3NaturalHeight;

    if (!widthMatch || !heightMatch) {
        return {
            matched: false,
            reason: `Dimension mismatch: Android screenshot (${androidGeometry.screenshotWidth}x${androidGeometry.screenshotHeight}) vs E3 intrinsic image (${e3NaturalWidth}x${e3NaturalHeight})`
        };
    }

    return { matched: true, reason: null };
}

export async function fetchDisplayGeometry(customFetch = globalThis.fetch) {
    if (typeof customFetch !== 'function') {
        return { success: false, reason: 'Fetch API unavailable' };
    }

    try {
        const response = await customFetch('/api/metadata');
        if (!response.ok) {
            return { success: false, reason: `HTTP error status ${response.status}` };
        }
        const data = await response.json();
        const schemaVal = validateGeometrySchema(data);
        if (!schemaVal.valid) {
            return { success: false, reason: schemaVal.reason };
        }
        return { success: true, geometry: data };
    } catch (e) {
        return { success: false, reason: `Network error: ${e.message}` };
    }
}
