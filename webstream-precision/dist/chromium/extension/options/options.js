/**
 * WebStream Precision Control - Settings / Options Script
 */

import { BrowserAPIWrapper } from '../shared/browser-api.js';
import { ConfigurationManager } from '../shared/configuration.js';
import { DEFAULT_SETTINGS } from '../shared/constants.js';

const browserAPI = new BrowserAPIWrapper();
const configManager = new ConfigurationManager(browserAPI);

document.addEventListener('DOMContentLoaded', async () => {
    const originsListEl = document.getElementById('originsList');
    const newOriginInputEl = document.getElementById('newOriginInput');
    const addOriginBtnEl = document.getElementById('addOriginBtn');
    const originErrEl = document.getElementById('originErr');
    const prefOverlayToggleEl = document.getElementById('prefOverlayToggle');
    const resetBtnEl = document.getElementById('resetBtn');
    const saveStatusEl = document.getElementById('saveStatus');

    let currentSettings = await configManager.loadSettings();

    function renderOrigins() {
        originsListEl.innerHTML = '';
        currentSettings.approvedOrigins.forEach((origin) => {
            const li = document.createElement('li');
            li.className = 'origin-item';

            const span = document.createElement('span');
            span.textContent = origin;

            const removeBtn = document.createElement('button');
            removeBtn.className = 'remove-btn';
            removeBtn.innerHTML = '🗑️';
            removeBtn.title = 'Remove Origin';
            removeBtn.addEventListener('click', async () => {
                await configManager.removeApprovedOrigin(origin);
                currentSettings = await configManager.loadSettings();
                renderOrigins();
                showStatus();
            });

            li.appendChild(span);
            li.appendChild(removeBtn);
            originsListEl.appendChild(li);
        });
    }

    function showStatus(text = 'Saved!') {
        saveStatusEl.textContent = text;
        saveStatusEl.style.display = 'inline';
        setTimeout(() => {
            saveStatusEl.style.display = 'none';
        }, 2000);
    }

    prefOverlayToggleEl.checked = currentSettings.overlayEnabled;

    prefOverlayToggleEl.addEventListener('change', async () => {
        currentSettings.overlayEnabled = prefOverlayToggleEl.checked;
        await configManager.saveSettings(currentSettings);
        showStatus();
    });

    addOriginBtnEl.addEventListener('click', async () => {
        originErrEl.style.display = 'none';
        const val = newOriginInputEl.value;
        const res = await configManager.addApprovedOrigin(val);
        if (res.success) {
            newOriginInputEl.value = '';
            currentSettings = await configManager.loadSettings();
            renderOrigins();
            showStatus('Origin Added!');
        } else {
            originErrEl.textContent = res.reason;
            originErrEl.style.display = 'block';
        }
    });

    resetBtnEl.addEventListener('click', async () => {
        if (confirm('Reset approved origins and preferences to defaults?')) {
            await configManager.saveSettings(DEFAULT_SETTINGS);
            currentSettings = await configManager.loadSettings();
            prefOverlayToggleEl.checked = currentSettings.overlayEnabled;
            renderOrigins();
            showStatus('Reset Completed!');
        }
    });

    renderOrigins();
});
