// auth.js - Master Admin Superuser & User Auth Handling
let currentUser = null;
let isMasterAdmin = false;

document.getElementById("loginForm").addEventListener("submit", function(e) {
    e.preventDefault();
    const email = document.getElementById("loginEmail").value.trim();
    const password = document.getElementById("loginPassword").value.trim();

    // Check for Master Admin vs Standard User Role
    if (email.toLowerCase().includes("admin")) {
        isMasterAdmin = true;
    } else {
        isMasterAdmin = false;
    }

    currentUser = { email: email, uid: "usr_" + btoa(email).substring(0, 10) };

    document.getElementById("loginScreen").classList.add("d-none");
    document.getElementById("dashboardApp").classList.remove("d-none");
    document.getElementById("userRoleBadge").innerText = isMasterAdmin ? "Role: 👑 Master Admin" : "Role: Standard User";

    if (!isMasterAdmin) {
        document.getElementById("adminTabBtn").classList.add("d-none");
    } else {
        document.getElementById("adminTabBtn").classList.remove("d-none");
    }

    // Load enrolled devices list
    loadEnrolledDevicesList();
});

document.addEventListener("DOMContentLoaded", function() {
    currentUser = { email: "admin@homemonitor.com", uid: "usr_" + btoa("admin@homemonitor.com").substring(0, 10) };
    if (typeof loadEnrolledDevicesList === "function") {
        loadEnrolledDevicesList();
    }
});

document.getElementById("logoutBtn").addEventListener("click", function() {
    currentUser = null;
    document.getElementById("loginScreen").classList.remove("d-none");
    document.getElementById("dashboardApp").classList.add("d-none");
});
