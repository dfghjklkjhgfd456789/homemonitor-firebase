/**
 * WebStream Precision Control - Popup Logic
 */

import { BrowserAPIWrapper } from '../shared/browser-api.js';
import { validateOrigin } from '../shared/origin-validator.js';
import { ConfigurationManager } from '../shared/configuration.js';
import { MESSAGE_TYPES } from '../shared/message-types.js';

const browserAPI = new BrowserAPIWrapper();
const configManager = new ConfigurationManager(browserAPI);

document.addEventListener('DOMContentLoaded', async () => {
    const originValEl = document.getElementById('originVal');
    const viewerStatusValEl = document.getElementById('viewerStatusVal');
    const naturalResValEl = document.getElementById('naturalResVal');
    const renderedResValEl = document.getElementById('renderedResVal');
    const dprValEl = document.getElementById('dprVal');
    const statusBadgeEl = document.getElementById('statusBadge');
    const overlayToggleEl = document.getElementById('overlayToggle');

    const settings = await configManager.loadSettings();
    overlayToggleEl.checked = settings.overlayEnabled;

    async function inspectActiveTab() {
        const tab = await browserAPI.getActiveTab();
        if (!tab || !tab.url) {
            originValEl.textContent = 'Unavailable';
            viewerStatusValEl.textContent = 'No Active Tab';
            statusBadgeEl.textContent = 'Inactive';
            statusBadgeEl.className = 'badge badge-inactive';
            return;
        }

        const urlVal = validateOrigin(tab.url);
        if (!urlVal.valid) {
            originValEl.textContent = tab.url.length > 25 ? tab.url.substring(0, 22) + '...' : tab.url;
            viewerStatusValEl.textContent = 'Unsupported Origin';
            statusBadgeEl.textContent = 'Unapproved Origin';
            statusBadgeEl.className = 'badge badge-inactive';
            return;
        }

        originValEl.textContent = urlVal.normalizedOrigin;

        // Query content script diagnostics
        const resp = await browserAPI.sendMessageToTab(tab.id, { type: MESSAGE_TYPES.GET_DIAGNOSTICS });
        if (resp && resp.viewer) {
            if (resp.viewer.detected) {
                viewerStatusValEl.textContent = 'Viewer Detected';
                statusBadgeEl.textContent = 'Active';
                statusBadgeEl.className = 'badge badge-active';
                naturalResValEl.textContent = `${resp.viewer.naturalWidth} x ${resp.viewer.naturalHeight}`;
                renderedResValEl.textContent = `${Math.round(resp.viewer.renderedWidth)} x ${Math.round(resp.viewer.renderedHeight)}`;
            } else {
                viewerStatusValEl.textContent = 'Viewer Loading / Not Found';
                statusBadgeEl.textContent = 'No Stream';
                statusBadgeEl.className = 'badge badge-inactive';
            }
            dprValEl.textContent = resp.display ? resp.display.devicePixelRatio : window.devicePixelRatio;
        } else {
            // Attempt script injection if tab authorized
            viewerStatusValEl.textContent = 'Injecting Content Script...';
            await browserAPI.executeScript(tab.id, {
                files: ['extension/content/bootstrap.js']
            });
            setTimeout(inspectActiveTab, 300);
        }
    }

    overlayToggleEl.addEventListener('change', async () => {
        const enabled = overlayToggleEl.checked;
        settings.overlayEnabled = enabled;
        await configManager.saveSettings(settings);

        const tab = await browserAPI.getActiveTab();
        if (tab && tab.id) {
            await browserAPI.sendMessageToTab(tab.id, {
                type: MESSAGE_TYPES.TOGGLE_OVERLAY,
                enabled
            });
        }
    });

    document.getElementById('refreshBtn').addEventListener('click', inspectActiveTab);
    document.getElementById('optionsBtn').addEventListener('click', () => {
        if (chrome && chrome.runtime && chrome.runtime.openOptionsPage) {
            chrome.runtime.openOptionsPage();
        } else {
            window.open('../options/options.html');
        }
    });

    inspectActiveTab();
});
