// firebase-config.js - Primary + 5 Fallback Firebase Account Pool Config
const firebaseAccountPool = [
    {
        name: "Primary Account",
        databaseURL: "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app",
        apiKey: "AIzaSyPrimaryDefaultKey12345"
    },
    {
        name: "Fallback Account #1",
        databaseURL: "https://homemonitor-fallback-1-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyOne12345"
    },
    {
        name: "Fallback Account #2",
        databaseURL: "https://homemonitor-fallback-2-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyTwo12345"
    },
    {
        name: "Fallback Account #3",
        databaseURL: "https://homemonitor-fallback-3-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyThree12345"
    },
    {
        name: "Fallback Account #4",
        databaseURL: "https://homemonitor-fallback-4-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyFour12345"
    },
    {
        name: "Fallback Account #5",
        databaseURL: "https://homemonitor-fallback-5-rtdb.firebaseio.com",
        apiKey: "AIzaSyFallbackKeyFive12345"
    }
];

let activeFirebaseIndex = 0;

function initActiveFirebaseInstance() {
    const config = firebaseAccountPool[activeFirebaseIndex];
    if (firebase.apps.length > 0) {
        firebase.app().delete();
    }
    firebase.initializeApp({
        databaseURL: config.databaseURL,
        apiKey: config.apiKey
    });
    console.log(`[Firebase C2] Active Instance: ${config.name} (${config.databaseURL})`);
}

function switchActiveFirebaseAccount(index) {
    if (index >= 0 && index < firebaseAccountPool.length) {
        activeFirebaseIndex = index;
        initActiveFirebaseInstance();
        renderFirebaseAccountPoolCards();
        if (typeof activeDeviceId !== "undefined" && activeDeviceId && typeof startRealtimeDeviceListener === "function") {
            startRealtimeDeviceListener(activeDeviceId);
        }
    }
}

function renderFirebaseAccountPoolCards() {
    const container = document.getElementById("firebaseAccountCardsContainer");
    if (!container) return;

    container.innerHTML = "";
    firebaseAccountPool.forEach((acc, idx) => {
        const col = document.createElement("div");
        col.className = "col-md-4";

        const isActive = (idx === activeFirebaseIndex);
        const card = document.createElement("div");
        card.className = `p-3 rounded border cursor-pointer ${isActive ? 'bg-dark text-light border-danger' : 'bg-light border-secondary'}`;
        card.onclick = function() { switchActiveFirebaseAccount(idx); };

        card.innerHTML = `
            <div class="d-flex justify-content-between align-items-center">
                <h6 class="fw-bold mb-1">${acc.name}</h6>
                <span class="badge ${isActive ? 'bg-danger' : 'bg-secondary'}">${isActive ? 'ACTIVE 🟢' : 'READY'}</span>
            </div>
            <p class="extra-small text-muted mb-0 text-truncate">${acc.databaseURL}</p>
        `;
        col.appendChild(card);
    });
}

document.addEventListener("DOMContentLoaded", function() {
    renderFirebaseAccountPoolCards();
});

// Initializing Primary Account
initActiveFirebaseInstance();
