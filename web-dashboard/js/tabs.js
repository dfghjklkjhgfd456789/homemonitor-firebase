// tabs.js - 42-Route Sidebar Navigation & Nested Social Chat Switching
document.querySelectorAll(".nav-link[data-tab]").forEach(link => {
    link.addEventListener("click", function() {
        document.querySelectorAll(".nav-link").forEach(l => l.classList.remove("active"));
        this.classList.add("active");

        const targetTabId = this.getAttribute("data-tab");
        const tabTitle = this.innerText;
        const routePath = this.getAttribute("data-route");
        const appKey = this.getAttribute("data-app");

        if (routePath && typeof navigateToRoute === "function") {
            navigateToRoute(routePath);
            return;
        }

        if (appKey && typeof loadSocialAppConversation === "function") {
            document.querySelectorAll(".tab-panel").forEach(panel => panel.classList.add("d-none"));
            const socialPanel = document.getElementById("tab-social-chat");
            if (socialPanel) {
                socialPanel.classList.remove("d-none");
                loadSocialAppConversation(appKey, tabTitle);
            }
            return;
        }

        document.querySelectorAll(".tab-panel").forEach(panel => panel.classList.add("d-none"));

        const targetPanel = document.getElementById(targetTabId);
        if (targetPanel) {
            targetPanel.classList.remove("d-none");
            if (typeof fetchTabDeviceData === "function") {
                fetchTabDeviceData(targetTabId);
            }
        } else {
            const genericPanel = document.getElementById("tab-generic");
            if (genericPanel) {
                document.getElementById("genericTabTitle").innerText = tabTitle;
                genericPanel.classList.remove("d-none");
                if (typeof fetchCategoryTelemetryLogs === "function") {
                    fetchCategoryTelemetryLogs(targetTabId);
                }
            }
        }
    });
});
