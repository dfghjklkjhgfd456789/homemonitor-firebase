// render-helpers.js - Reusable UI Rendering Helpers & Formatting Utilities

function renderLoadingSpinner(containerId, message = "Fetching live device data over WebRTC DataChannel...") {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = `
        <div class="text-center py-4 my-2">
            <div class="spinner-border text-primary me-2" role="status" style="width: 2rem; height: 2rem;">
                <span class="visually-hidden">Loading...</span>
            </div>
            <div class="small fw-bold text-secondary mt-2">${safeEscapeHtml(message)}</div>
        </div>
    `;
}

function renderEmptyState(containerId, title = "No records found", subtitle = "Awaiting live activity from target device.") {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = `
        <div class="p-4 text-center text-muted my-2 bg-light rounded border">
            <i class="fa-solid fa-inbox display-6 text-secondary mb-2 opacity-50 d-block"></i>
            <strong class="text-dark small">${safeEscapeHtml(title)}</strong>
            <small class="d-block text-secondary extra-small mt-1 opacity-75">${safeEscapeHtml(subtitle)}</small>
        </div>
    `;
}

function renderErrorState(containerId, errorMsg = "Failed to load data", retryFnName = null) {
    const el = document.getElementById(containerId);
    if (!el) return;
    const retryBtn = retryFnName ? `<button class="btn btn-sm btn-outline-danger mt-2 fw-bold extra-small" onclick="${retryFnName}()"><i class="fa-solid fa-arrows-rotate me-1"></i>Retry Request</button>` : "";
    el.innerHTML = `
        <div class="alert alert-danger p-3 my-2 text-center small border-danger">
            <i class="fa-solid fa-circle-exclamation me-1"></i><strong>Data Fetch Warning:</strong> ${safeEscapeHtml(errorMsg)}
            ${retryBtn}
        </div>
    `;
}

function renderUnsupportedState(containerId, featureName = "Feature") {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = `
        <div class="p-4 text-center text-muted my-2 bg-light rounded border">
            <i class="fa-solid fa-ban display-6 text-warning mb-2 d-block"></i>
            <strong class="text-dark small">${safeEscapeHtml(featureName)} Unsupported</strong>
            <small class="d-block text-secondary extra-small mt-1 opacity-75">This feature is not supported on the target device's current Android build or platform version.</small>
        </div>
    `;
}

function formatTimestamp(ts) {
    if (!ts || isNaN(ts)) return "Just now";
    const numTs = Number(ts);
    if (numTs <= 0) return "Just now";
    const date = new Date(numTs);
    if (isNaN(date.getTime())) return "Just now";
    const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const dateStr = date.toLocaleDateString([], { month: 'short', day: 'numeric' });
    return `${timeStr} · ${dateStr}`;
}

function formatBytes(bytes) {
    if (!bytes || isNaN(bytes) || bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB", "TB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

function safeEscapeHtml(text) {
    if (!text) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

if (typeof module !== "undefined" && module.exports) {
    module.exports = { renderLoadingSpinner, renderEmptyState, renderErrorState, renderUnsupportedState, formatTimestamp, formatBytes, safeEscapeHtml };
}
