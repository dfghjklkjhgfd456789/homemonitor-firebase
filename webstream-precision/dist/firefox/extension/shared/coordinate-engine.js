/**
 * WebStream Precision Control - Shared Coordinate Engine (E3)
 * Pure JavaScript module for sub-pixel image content normalization.
 * Fully independent of browser extension APIs (chrome and browser).
 */

let geometryVersionCounter = 1;

export function parseObjectPosition(positionStr, contentWidth, contentHeight, renderedWidth, renderedHeight) {
    const rawParts = (positionStr || '50% 50%').trim().split(/\s+/);
    let p1 = rawParts[0] || '50%';
    let p2 = rawParts[1] || (rawParts[0] === 'top' || rawParts[0] === 'bottom' ? 'center' : rawParts[0]) || '50%';

    const horizKeywords = { left: '0%', center: '50%', right: '100%' };
    const vertKeywords = { top: '0%', center: '50%', bottom: '100%' };

    let hStr = '50%';
    let vStr = '50%';

    if (p1 === 'top' || p1 === 'bottom') {
        vStr = vertKeywords[p1];
        hStr = horizKeywords[p2] || p2;
    } else if (p2 === 'top' || p2 === 'bottom') {
        vStr = vertKeywords[p2];
        hStr = horizKeywords[p1] || p1;
    } else {
        hStr = horizKeywords[p1] || p1;
        vStr = vertKeywords[p2] || p2;
    }

    let offsetX = 0;
    let offsetY = 0;

    if (hStr.endsWith('%')) {
        const pct = parseFloat(hStr) / 100;
        offsetX = (contentWidth - renderedWidth) * pct;
    } else if (hStr.endsWith('px')) {
        offsetX = parseFloat(hStr);
    } else {
        const num = parseFloat(hStr);
        offsetX = isNaN(num) ? (contentWidth - renderedWidth) * 0.5 : num;
    }

    if (vStr.endsWith('%')) {
        const pct = parseFloat(vStr) / 100;
        offsetY = (contentHeight - renderedHeight) * pct;
    } else if (vStr.endsWith('px')) {
        offsetY = parseFloat(vStr);
    } else {
        const num = parseFloat(vStr);
        offsetY = isNaN(num) ? (contentHeight - renderedHeight) * 0.5 : num;
    }

    return { offsetX, offsetY };
}

export function getImageGeometry(imageElement, mockLayout = null) {
    if (!imageElement && !mockLayout) {
        return {
            status: 'invalid',
            reason: 'Image element or mock layout reference is missing'
        };
    }

    let naturalWidth = 0;
    let naturalHeight = 0;
    let borderBox = { left: 0, top: 0, width: 0, height: 0 };
    let borderLeft = 0, borderRight = 0, borderTop = 0, borderBottom = 0;
    let paddingLeft = 0, paddingRight = 0, paddingTop = 0, paddingBottom = 0;
    let objectFit = 'contain';
    let objectPosition = '50% 50%';
    let transformStr = 'none';

    if (mockLayout) {
        naturalWidth = mockLayout.naturalWidth || 0;
        naturalHeight = mockLayout.naturalHeight || 0;
        borderBox = mockLayout.borderBox || { left: 0, top: 0, width: 0, height: 0 };
        borderLeft = mockLayout.borderLeft || 0;
        borderRight = mockLayout.borderRight || 0;
        borderTop = mockLayout.borderTop || 0;
        borderBottom = mockLayout.borderBottom || 0;
        paddingLeft = mockLayout.paddingLeft || 0;
        paddingRight = mockLayout.paddingRight || 0;
        paddingTop = mockLayout.paddingTop || 0;
        paddingBottom = mockLayout.paddingBottom || 0;
        objectFit = mockLayout.objectFit || 'contain';
        objectPosition = mockLayout.objectPosition || '50% 50%';
        transformStr = mockLayout.transform || 'none';
    } else {
        naturalWidth = imageElement.naturalWidth || 0;
        naturalHeight = imageElement.naturalHeight || 0;
        const rect = imageElement.getBoundingClientRect();
        borderBox = { left: rect.left, top: rect.top, width: rect.width, height: rect.height };

        if (globalThis.getComputedStyle) {
            const style = globalThis.getComputedStyle(imageElement);
            borderLeft = parseFloat(style.borderLeftWidth) || 0;
            borderRight = parseFloat(style.borderRightWidth) || 0;
            borderTop = parseFloat(style.borderTopWidth) || 0;
            borderBottom = parseFloat(style.borderBottomWidth) || 0;
            paddingLeft = parseFloat(style.paddingLeft) || 0;
            paddingRight = parseFloat(style.paddingRight) || 0;
            paddingTop = parseFloat(style.paddingTop) || 0;
            paddingBottom = parseFloat(style.paddingBottom) || 0;
            objectFit = style.objectFit || 'contain';
            objectPosition = style.objectPosition || '50% 50%';
            transformStr = style.transform || 'none';
        }
    }

    if (naturalWidth <= 0 || naturalHeight <= 0) {
        return {
            status: 'invalid',
            reason: 'Image intrinsic dimensions (naturalWidth/naturalHeight) are zero or negative'
        };
    }

    // Content box dimensions
    const contentLeft = borderBox.left + borderLeft + paddingLeft;
    const contentTop = borderBox.top + borderTop + paddingTop;
    const contentWidth = Math.max(0, borderBox.width - (borderLeft + borderRight + paddingLeft + paddingRight));
    const contentHeight = Math.max(0, borderBox.height - (borderTop + borderBottom + paddingTop + paddingBottom));

    if (contentWidth <= 0 || contentHeight <= 0) {
        return {
            status: 'invalid',
            reason: 'Image content-box width or height is zero'
        };
    }

    // Check transforms
    let supportedTransform = true;
    let unsupportedReason = null;
    if (transformStr && transformStr !== 'none') {
        // Parse 2D matrix: matrix(a, b, c, d, tx, ty)
        if (transformStr.startsWith('matrix(')) {
            const values = transformStr.slice(7, -1).split(',').map(v => parseFloat(v.trim()));
            const [a, b, c, d] = values;
            // Allow uniform or axis-aligned scale/translate: b === 0 and c === 0
            if (Math.abs(b) > 0.001 || Math.abs(c) > 0.001) {
                supportedTransform = false;
                unsupportedReason = 'Rotation or skew transform is not supported';
            }
        } else if (transformStr.includes('rotate') || transformStr.includes('skew')) {
            supportedTransform = false;
            unsupportedReason = 'Complex CSS transform (rotate/skew) is not supported';
        }
    }

    // Calculate Rendered Object Dimensions based on object-fit
    let renderedWidth = contentWidth;
    let renderedHeight = contentHeight;

    switch (objectFit) {
        case 'fill':
            renderedWidth = contentWidth;
            renderedHeight = contentHeight;
            break;

        case 'contain': {
            const scale = Math.min(contentWidth / naturalWidth, contentHeight / naturalHeight);
            renderedWidth = naturalWidth * scale;
            renderedHeight = naturalHeight * scale;
            break;
        }

        case 'cover': {
            const scale = Math.max(contentWidth / naturalWidth, contentHeight / naturalHeight);
            renderedWidth = naturalWidth * scale;
            renderedHeight = naturalHeight * scale;
            break;
        }

        case 'none':
            renderedWidth = naturalWidth;
            renderedHeight = naturalHeight;
            break;

        case 'scale-down': {
            const scale = Math.min(1.0, contentWidth / naturalWidth, contentHeight / naturalHeight);
            renderedWidth = naturalWidth * scale;
            renderedHeight = naturalHeight * scale;
            break;
        }

        default:
            // Fallback contain
            const scale = Math.min(contentWidth / naturalWidth, contentHeight / naturalHeight);
            renderedWidth = naturalWidth * scale;
            renderedHeight = naturalHeight * scale;
            break;
    }

    // Calculate position offset inside content box
    const { offsetX, offsetY } = parseObjectPosition(objectPosition, contentWidth, contentHeight, renderedWidth, renderedHeight);

    const renderedLeft = contentLeft + offsetX;
    const renderedTop = contentTop + offsetY;

    return {
        status: 'valid',
        version: geometryVersionCounter++,
        naturalWidth,
        naturalHeight,
        borderBox,
        contentBox: {
            left: contentLeft,
            top: contentTop,
            width: contentWidth,
            height: contentHeight
        },
        renderedImage: {
            left: renderedLeft,
            top: renderedTop,
            width: renderedWidth,
            height: renderedHeight
        },
        objectFit,
        objectPosition,
        supportedTransform,
        unsupportedReason
    };
}

export function validateNormalizedPoint(u, v) {
    return typeof u === 'number' && typeof v === 'number' &&
           !isNaN(u) && !isNaN(v) && isFinite(u) && isFinite(v) &&
           u >= 0.0 && u <= 1.0 && v >= 0.0 && v <= 1.0;
}

export function mapClientPointToNormalized(clientX, clientY, geometry) {
    if (!geometry || geometry.status !== 'valid') {
        return {
            valid: false,
            reason: geometry ? (geometry.reason || 'Invalid geometry status') : 'Geometry missing'
        };
    }

    if (!geometry.supportedTransform) {
        return {
            valid: false,
            reason: geometry.unsupportedReason || 'Unsupported CSS transform'
        };
    }

    const { contentBox, renderedImage, naturalWidth, naturalHeight } = geometry;

    // Reject pointer if landing outside CSS content box
    if (clientX < contentBox.left || clientX > contentBox.left + contentBox.width ||
        clientY < contentBox.top || clientY > contentBox.top + contentBox.height) {
        return {
            valid: false,
            reason: 'Pointer is outside visible CSS content box'
        };
    }

    // Calculate position relative to rendered image object
    const pixelX = clientX - renderedImage.left;
    const pixelY = clientY - renderedImage.top;

    const u = pixelX / renderedImage.width;
    const v = pixelY / renderedImage.height;

    // Check bounds [0.0, 1.0]
    if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
        return {
            valid: false,
            u,
            v,
            reason: 'Pointer landed in letterbox/pillarbox region'
        };
    }

    // Screenshot pixel coordinates (continuous floating-point precision)
    const sourceX = u * naturalWidth;
    const sourceY = v * naturalHeight;

    return {
        valid: true,
        u,
        v,
        sourceX,
        sourceY,
        naturalWidth,
        naturalHeight,
        reason: null
    };
}

export function getCoordinateDiagnostics(geometry, clientX = null, clientY = null) {
    if (!geometry || geometry.status !== 'valid') {
        return {
            valid: false,
            reason: geometry ? geometry.reason : 'No geometry available'
        };
    }

    let pointMapping = null;
    if (typeof clientX === 'number' && typeof clientY === 'number') {
        pointMapping = mapClientPointToNormalized(clientX, clientY, geometry);
    }

    return {
        valid: geometry.status === 'valid',
        version: geometry.version,
        objectFit: geometry.objectFit,
        objectPosition: geometry.objectPosition,
        naturalWidth: geometry.naturalWidth,
        naturalHeight: geometry.naturalHeight,
        borderBox: geometry.borderBox,
        contentBox: geometry.contentBox,
        renderedImage: geometry.renderedImage,
        pointMapping
    };
}
