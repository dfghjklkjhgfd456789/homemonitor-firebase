/**
 * WebStream Precision Control - Calibration Engine (E6)
 * Generates 9-point grid targets and computes coordinate error metrics (Mean, RMS, P95).
 */

export const NINE_POINT_GRID = [
    { id: 1, name: 'Top-Left', u: 0.10, v: 0.10 },
    { id: 2, name: 'Top-Center', u: 0.50, v: 0.10 },
    { id: 3, name: 'Top-Right', u: 0.90, v: 0.10 },
    { id: 4, name: 'Center-Left', u: 0.10, v: 0.50 },
    { id: 5, name: 'Center', u: 0.50, v: 0.50 },
    { id: 6, name: 'Center-Right', u: 0.90, v: 0.50 },
    { id: 7, name: 'Bottom-Left', u: 0.10, v: 0.90 },
    { id: 8, name: 'Bottom-Center', u: 0.50, v: 0.90 },
    { id: 9, name: 'Bottom-Right', u: 0.90, v: 0.90 }
];

export function getNinePointGrid() {
    return [...NINE_POINT_GRID];
}

export function computeErrorVectors(targets, observed, screenW = 1080, screenH = 2400) {
    if (!Array.isArray(targets) || !Array.isArray(observed) || targets.length !== observed.length) {
        return { valid: false, reason: 'Mismatched target vs observed points' };
    }

    const errors = [];
    let sumErr = 0;
    let sumSquare = 0;

    for (let i = 0; i < targets.length; i++) {
        const tgt = targets[i];
        const obs = observed[i];

        const expectedX = tgt.u * screenW;
        const expectedY = tgt.v * screenH;

        const dx = obs.x - expectedX;
        const dy = obs.y - expectedY;
        const dist = Math.sqrt(dx * dx + dy * dy);

        errors.push({
            id: tgt.id,
            name: tgt.name,
            expectedX,
            expectedY,
            observedX: obs.x,
            observedY: obs.y,
            errorPx: dist
        });

        sumErr += dist;
        sumSquare += dist * dist;
    }

    const errorDistances = errors.map(e => e.errorPx).sort((a, b) => a - b);
    const meanErrorPx = sumErr / errors.length;
    const rmsErrorPx = Math.sqrt(sumSquare / errors.length);
    const maxErrorPx = errorDistances[errorDistances.length - 1];
    const p95Idx = Math.min(Math.floor(errors.length * 0.95), errors.length - 1);
    const p95ErrorPx = errorDistances[p95Idx];

    const shorterDim = Math.min(screenW, screenH);
    const normalizedErrorPct = (p95ErrorPx / shorterDim) * 100;

    return {
        valid: true,
        meanErrorPx,
        rmsErrorPx,
        maxErrorPx,
        p95ErrorPx,
        normalizedErrorPct,
        errors
    };
}
