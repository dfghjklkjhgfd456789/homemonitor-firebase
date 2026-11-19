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
                console.error("[Firebase Auth] Login Error:", error);
                alert("Authentication Failed: " + error.message);
            });
    } else {
        // Fallback for offline UI testing mode
        isMasterAdmin = email.toLowerCase().includes("admin");
        currentUser = { email: email, uid: "usr_" + btoa(email).substring(0, 10) };
        document.getElementById("loginScreen").classList.add("d-none");
        document.getElementById("dashboardApp").classList.remove("d-none");
        document.getElementById("userRoleBadge").innerText = isMasterAdmin ? "Role: 👑 Master Admin" : "Role: Standard User";
        if (typeof loadEnrolledDevicesList === "function") {
            loadEnrolledDevicesList();
        }
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
