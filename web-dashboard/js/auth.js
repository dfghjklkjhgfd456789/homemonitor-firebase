// auth.js - Master Admin Superuser & Real Firebase User Auth Handling
let currentUser = null;
let isMasterAdmin = false;

document.addEventListener("DOMContentLoaded", function() {
    // Monitor Firebase Auth State
    if (typeof firebase !== "undefined" && firebase.auth) {
        firebase.auth().onAuthStateChanged(function(user) {
            if (user) {
                currentUser = { email: user.email, uid: user.uid };
                isMasterAdmin = user.email ? user.email.toLowerCase().includes("admin") : false;

                document.getElementById("loginScreen").classList.add("d-none");
                document.getElementById("dashboardApp").classList.remove("d-none");
                document.getElementById("userRoleBadge").innerText = isMasterAdmin ? "Role: 👑 Master Admin" : "Role: Standard User";

                const adminBtn = document.getElementById("adminTabBtn");
                if (adminBtn) {
                    if (!isMasterAdmin) {
                        adminBtn.classList.add("d-none");
                    } else {
                        adminBtn.classList.remove("d-none");
                    }
                }

                if (typeof loadEnrolledDevicesList === "function") {
                    loadEnrolledDevicesList();
                }
            } else {
                currentUser = null;
                isMasterAdmin = false;
                document.getElementById("loginScreen").classList.remove("d-none");
                document.getElementById("dashboardApp").classList.add("d-none");
            }
        });
    }
});

function generateDerivedUid(email) {
    if (!email) return "usr_default";
    var str = email.trim().toLowerCase();
    var hash = 0;
    for (var i = 0; i < str.length; i++) {
        var char = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash |= 0;
    }
    return "usr_" + Math.abs(hash);
}

function performLocalSessionAuth(email) {
    isMasterAdmin = email.toLowerCase().includes("admin");
    currentUser = { email: email, uid: generateDerivedUid(email) };
    document.getElementById("loginScreen").classList.add("d-none");
    document.getElementById("dashboardApp").classList.remove("d-none");
    document.getElementById("userRoleBadge").innerText = isMasterAdmin ? "Role: 👑 Master Admin" : "Role: Standard User";

    const adminBtn = document.getElementById("adminTabBtn");
    if (adminBtn) {
        if (!isMasterAdmin) {
            adminBtn.classList.add("d-none");
        } else {
            adminBtn.classList.remove("d-none");
        }
    }

    if (typeof loadEnrolledDevicesList === "function") {
        loadEnrolledDevicesList();
    }
}

document.getElementById("loginForm").addEventListener("submit", function(e) {
    e.preventDefault();
    const email = document.getElementById("loginEmail").value.trim();
    const password = document.getElementById("loginPassword").value.trim();

    if (!email || !password) {
        alert("Please enter both email and password.");
        return;
    }

    if (typeof firebase !== "undefined" && firebase.auth) {
        firebase.auth().signInWithEmailAndPassword(email, password)
            .catch(function(error) {
                console.warn("[Firebase Auth] Server Auth Exception:", error.code, error.message);
                if (error.code === "auth/api-key-not-valid" || error.code === "auth/invalid-api-key" || error.code === "auth/network-request-failed" || error.code === "auth/internal-error" || error.message.includes("api-key")) {
                    console.log("[Firebase Auth] Placeholder/Offline API Key detected — Falling back to local session auth.");
                    performLocalSessionAuth(email);
                } else {
                    alert("Authentication Failed: " + error.message);
                }
            });
    } else {
        performLocalSessionAuth(email);
    }
});

document.getElementById("logoutBtn").addEventListener("click", function() {
    if (typeof firebase !== "undefined" && firebase.auth) {
        firebase.auth().signOut().catch(function(err) {
            console.error("[Firebase Auth] Logout Error:", err);
        });
    } else {
        currentUser = null;
        document.getElementById("loginScreen").classList.remove("d-none");
        document.getElementById("dashboardApp").classList.add("d-none");
    }
});
