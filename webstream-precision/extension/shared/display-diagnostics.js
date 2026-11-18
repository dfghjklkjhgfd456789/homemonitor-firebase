/**
 * WebStream Precision Control - Display Diagnostics
 */

export function getDisplayDiagnostics(windowRef = globalThis.window) {
    if (!windowRef) {
        return {
            viewportWidth: 0,
            viewportHeight: 0,
            devicePixelRatio: 1,
            scrollX: 0,
            scrollY: 0
        };
    }

    return {
        viewportWidth: windowRef.innerWidth || 0,
        viewportHeight: windowRef.innerHeight || 0,
        devicePixelRatio: windowRef.devicePixelRatio || 1,
        scrollX: windowRef.scrollX || 0,
        scrollY: windowRef.scrollY || 0,
        userAgent: windowRef.navigator ? windowRef.navigator.userAgent : 'Unknown'
    };
}
