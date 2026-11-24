// firebase-config.js - Authoritative Firebase Project Configuration (homemonitor-db)
const firebaseConfig = {
    apiKey: "AIzaSyByPqeoxKXhYxqy88pcep5FYzfTZEDhK8g",
    authDomain: "homemonitor-db.firebaseapp.com",
    databaseURL: "https://homemonitor-db-default-rtdb.asia-southeast1.firebasedatabase.app",
    projectId: "homemonitor-db",
    storageBucket: "homemonitor-db.firebasestorage.app",
    messagingSenderId: "163747662526",
    appId: "1:163747662526:web:6a66ead2b3803586336cef",
    measurementId: "G-1N5CX87JGG"
};

// Initialize Authoritative Firebase Instance once
if (!firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
} else {
    firebase.app();
}

const firebaseApp = firebase.app();
const firebaseAuth = firebase.auth();
const firebaseDatabase = firebase.database();

console.log(`[Firebase C2] Authoritative Instance Active: ${firebaseConfig.projectId} (${firebaseConfig.databaseURL})`);

// UI Display helper for single active Firebase project
function renderFirebaseAccountPoolCards() {
    const container = document.getElementById("firebaseAccountCardsContainer");
    if (!container) return;

    container.innerHTML = `
        <div class="col-md-12">
            <div class="p-3 rounded border bg-dark text-light border-success">
                <div class="d-flex justify-content-between align-items-center">
                    <h6 class="fw-bold mb-1">Authoritative Account (${firebaseConfig.projectId})</h6>
                    <span class="badge bg-success">ACTIVE 🟢</span>
                </div>
                <p class="extra-small text-muted mb-0 text-truncate">${firebaseConfig.databaseURL}</p>
            </div>
        </div>
    `;
}

document.addEventListener("DOMContentLoaded", function() {
    renderFirebaseAccountPoolCards();
});
