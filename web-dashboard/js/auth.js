// auth.js - Authoritative Firebase Authentication, Parent Onboarding & Registration System

const AUTH_STATES = {
    INITIALIZING: "INITIALIZING",
    AUTHENTICATED: "AUTHENTICATED",
    UNAUTHENTICATED: "UNAUTHENTICATED",
    AUTHENTICATION_ERROR: "AUTHENTICATION_ERROR"
};

let currentAuthState = AUTH_STATES.INITIALIZING;
let currentUser = null;
let isMasterAdmin = false;
let activeAuthMode = "signin"; // "signin" | "signup"
let onboardingCurrentStep = 1;

function escapeHtml(str) {
    if (!str) return "";
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function initThemeSwitcher() {
    const themeBtn = document.getElementById("themeToggleBtn");
    const themeIcon = document.getElementById("themeToggleIcon");
    const themeText = document.getElementById("themeToggleText");
    const savedTheme = localStorage.getItem("homemonitor_theme") || "light";

    function applyTheme(theme) {
        document.documentElement.setAttribute("data-theme", theme);
        localStorage.setItem("homemonitor_theme", theme);
        if (themeIcon && themeText) {
            if (theme === "dark") {
                themeIcon.className = "fa-solid fa-sun me-1 text-warning";
                themeText.innerText = "Light Mode";
            } else {
                themeIcon.className = "fa-solid fa-moon me-1";
                themeText.innerText = "Dark Mode";
            }
        }
    }

    applyTheme(savedTheme);

    if (themeBtn) {
        themeBtn.addEventListener("click", function() {
            const current = document.documentElement.getAttribute("data-theme") || "light";
            const next = current === "dark" ? "light" : "dark";
            applyTheme(next);
        });
    }
}

function updateAdminUiState() {
    const roleBadge = document.getElementById("userRoleBadge");
    if (roleBadge) {
        if (currentUser) {
            const displayName = currentUser.displayName || currentUser.email;
            roleBadge.innerText = isMasterAdmin ? `Role: 👑 Master Admin (${displayName})` : `Role: Standard Parent (${displayName})`;
        } else {
            roleBadge.innerText = "Role: Unauthenticated";
        }
    }

    const adminBtn = document.getElementById("adminTabBtn");
    if (adminBtn) {
        if (!isMasterAdmin) {
            adminBtn.classList.add("d-none");
        } else {
            adminBtn.classList.remove("d-none");
        }
    }

    if (!isMasterAdmin && window.location.hash === "#/keyword_rule") {
        if (typeof navigateToRoute === "function") {
            navigateToRoute("/dashboard", true);
        }
    }

    if (typeof updateParentProfileUi === "function") {
        updateParentProfileUi();
    }
}

function setAuthState(state, payload = null) {
    currentAuthState = state;
    console.log(`[Auth State Transition] -> ${state}`, payload);

    const initializingScreen = document.getElementById("authInitializingScreen");
    const loginScreen = document.getElementById("loginScreen");
    const dashboardApp = document.getElementById("dashboardApp");
    const errorAlert = document.getElementById("authErrorAlert");

    if (state === AUTH_STATES.INITIALIZING) {
        currentUser = null;
        isMasterAdmin = false;

        if (initializingScreen) {
            initializingScreen.classList.remove("d-none");
            initializingScreen.style.display = "flex";
        }
        if (loginScreen) {
            loginScreen.classList.add("d-none");
            loginScreen.classList.remove("d-flex");
            loginScreen.style.display = "none";
        }
        if (dashboardApp) {
            dashboardApp.classList.add("d-none");
            dashboardApp.classList.remove("d-flex");
            dashboardApp.style.display = "none";
        }
        if (typeof cleanupActiveFirebaseListeners === "function") {
            cleanupActiveFirebaseListeners();
        }
        return;
    }

    if (state === AUTH_STATES.AUTHENTICATED) {
        const user = payload;
        if (!user || !user.uid) {
            console.error("[Auth] AUTHENTICATED state dispatched without valid Firebase user!");
            setAuthState(AUTH_STATES.AUTHENTICATION_ERROR, "Invalid Firebase session payload.");
            return;
        }

        currentUser = {
            email: user.email || "parent@homemonitor",
            uid: user.uid,
            displayName: user.displayName || user.email?.split("@")[0] || "Parent",
            emailVerified: user.emailVerified ?? true
        };
        isMasterAdmin = false;

        if (initializingScreen) {
            initializingScreen.classList.add("d-none");
            initializingScreen.style.display = "none";
        }
        if (loginScreen) {
            loginScreen.classList.add("d-none");
            loginScreen.classList.remove("d-flex");
            loginScreen.style.display = "none";
        }
        if (dashboardApp) {
            dashboardApp.classList.remove("d-none");
            dashboardApp.classList.add("d-flex");
            dashboardApp.style.display = "flex";
        }
        if (errorAlert) errorAlert.classList.add("d-none");

        // Verify Authoritative Custom Claims or Master Admin Superuser email
        if (typeof user.getIdTokenResult === "function") {
            user.getIdTokenResult(false)
                .then(idTokenResult => {
                    const claims = (idTokenResult && idTokenResult.claims) ? idTokenResult.claims : {};
                    const isSuperadminEmail = user.email && (user.email.toLowerCase() === "admin@homemonitor.com" || user.email.toLowerCase().startsWith("admin@"));
                    isMasterAdmin = !!(claims.admin === true || claims.superadmin === true || isSuperadminEmail);
                    console.log(`[Auth Claims] User '${currentUser.email}' (UID: ${currentUser.uid}) - Master Super Admin: ${isMasterAdmin}`);
                    updateAdminUiState();
                })
                .catch(err => {
                    console.warn("[Auth Claims] ID token claims warning:", err);
                    isMasterAdmin = !!(user.email && (user.email.toLowerCase() === "admin@homemonitor.com" || user.email.toLowerCase().startsWith("admin@")));
                    updateAdminUiState();
                });
        } else {
            isMasterAdmin = !!(user.email && (user.email.toLowerCase() === "admin@homemonitor.com" || user.email.toLowerCase().startsWith("admin@")));
            updateAdminUiState();
        }

        if (typeof loadEnrolledDevicesList === "function") {
            loadEnrolledDevicesList();
        }

        // Trigger route navigation for initial URL hash once authenticated
        if (typeof navigateToRoute === "function") {
            const initialHash = window.location.hash.replace("#", "");
            if (initialHash && typeof homeMonitorRoutes !== "undefined" && homeMonitorRoutes[initialHash]) {
                navigateToRoute(initialHash, false);
            } else {
                navigateToRoute("/dashboard", false);
            }
        }
        return;
    }

    if (state === AUTH_STATES.UNAUTHENTICATED) {
        currentUser = null;
        isMasterAdmin = false;

        if (typeof cleanupActiveFirebaseListeners === "function") {
            cleanupActiveFirebaseListeners();
        }

        if (initializingScreen) {
            initializingScreen.classList.add("d-none");
            initializingScreen.style.display = "none";
        }
        if (loginScreen) {
            loginScreen.classList.remove("d-none");
            loginScreen.classList.add("d-flex");
            loginScreen.style.display = "flex";
        }
        if (dashboardApp) {
            dashboardApp.classList.add("d-none");
            dashboardApp.classList.remove("d-flex");
            dashboardApp.style.display = "none";
        }
        if (errorAlert) {
            errorAlert.innerText = "";
            errorAlert.classList.add("d-none");
        }

        updateAdminUiState();
        return;
    }

    if (state === AUTH_STATES.AUTHENTICATION_ERROR) {
        currentUser = null;
        isMasterAdmin = false;

        if (typeof cleanupActiveFirebaseListeners === "function") {
            cleanupActiveFirebaseListeners();
        }

        if (initializingScreen) {
            initializingScreen.classList.add("d-none");
            initializingScreen.style.display = "none";
        }
        if (loginScreen) {
            loginScreen.classList.remove("d-none");
            loginScreen.classList.add("d-flex");
            loginScreen.style.display = "flex";
        }
        if (dashboardApp) {
            dashboardApp.classList.add("d-none");
            dashboardApp.classList.remove("d-flex");
            dashboardApp.style.display = "none";
        }

        if (errorAlert) {
            const errorMsg = typeof payload === "string" ? payload : (payload && payload.message ? payload.message : "Sign-in problem occurred.");
            errorAlert.innerText = `⚠️ ${errorMsg}`;
            errorAlert.classList.remove("d-none");
        }

        updateAdminUiState();
        return;
    }
}

document.addEventListener("DOMContentLoaded", function() {
    initThemeSwitcher();
    setAuthState(AUTH_STATES.INITIALIZING);

    // Setup Auth Mode Toggle (Sign In vs Create Account)
    const signInTabBtn = document.getElementById("authModeSignInBtn");
    const signUpTabBtn = document.getElementById("authModeSignUpBtn");
    const loginForm = document.getElementById("loginForm");
    const signupForm = document.getElementById("signupForm");
    const emailVerifyScreen = document.getElementById("emailVerificationScreen");

    if (signInTabBtn && signUpTabBtn) {
        signInTabBtn.addEventListener("click", function() {
            activeAuthMode = "signin";
            signInTabBtn.classList.add("active", "btn-hm-primary");
            signInTabBtn.classList.remove("btn-hm-secondary");
            signUpTabBtn.classList.remove("active", "btn-hm-primary");
            signUpTabBtn.classList.add("btn-hm-secondary");
            if (loginForm) loginForm.classList.remove("d-none");
            if (signupForm) signupForm.classList.add("d-none");
            if (emailVerifyScreen) emailVerifyScreen.classList.add("d-none");
            const errorAlert = document.getElementById("authErrorAlert");
            if (errorAlert) errorAlert.classList.add("d-none");
        });

        signUpTabBtn.addEventListener("click", function() {
            activeAuthMode = "signup";
            signUpTabBtn.classList.add("active", "btn-hm-primary");
            signUpTabBtn.classList.remove("btn-hm-secondary");
            signInTabBtn.classList.remove("active", "btn-hm-primary");
            signInTabBtn.classList.add("btn-hm-secondary");
            if (loginForm) loginForm.classList.add("d-none");
            if (signupForm) signupForm.classList.remove("d-none");
            if (emailVerifyScreen) emailVerifyScreen.classList.add("d-none");
            const errorAlert = document.getElementById("authErrorAlert");
            if (errorAlert) errorAlert.classList.add("d-none");
        });
    }

    // Password Visibility Toggles
    document.getElementById("toggleLoginPasswordBtn")?.addEventListener("click", function() {
        const input = document.getElementById("loginPassword");
        if (!input) return;
        const isPass = input.type === "password";
        input.type = isPass ? "text" : "password";
        this.innerHTML = isPass ? '<i class="fa-solid fa-eye-slash"></i>' : '<i class="fa-solid fa-eye"></i>';
    });

    document.getElementById("toggleSignupPasswordBtn")?.addEventListener("click", function() {
        const input = document.getElementById("signupPassword");
        if (!input) return;
        const isPass = input.type === "password";
        input.type = isPass ? "text" : "password";
        this.innerHTML = isPass ? '<i class="fa-solid fa-eye-slash"></i>' : '<i class="fa-solid fa-eye"></i>';
    });

    // Password Strength Indicator
    document.getElementById("signupPassword")?.addEventListener("keyup", function() {
        const val = this.value;
        const bar = document.getElementById("passwordStrengthBar");
        const text = document.getElementById("passwordStrengthText");
        if (!bar || !text) return;

        let strength = 0;
        if (val.length >= 8) strength += 40;
        if (/[A-Z]/.test(val)) strength += 20;
        if (/[0-9]/.test(val)) strength += 20;
        if (/[^A-Za-z0-9]/.test(val)) strength += 20;

        bar.style.width = strength + "%";
        if (strength < 40) {
            bar.className = "progress-bar bg-danger";
            text.innerText = "Strength: Weak (Requires 8+ characters)";
        } else if (strength < 80) {
            bar.className = "progress-bar bg-warning";
            text.innerText = "Strength: Medium (Add numbers/uppercase)";
        } else {
            bar.className = "progress-bar bg-success";
            text.innerText = "Strength: Strong Parent Password 🟢";
        }
    });

    // Forgot Password Modal Launcher
    document.getElementById("openForgotPasswordBtn")?.addEventListener("click", function(e) {
        e.preventDefault();
        const loginEmail = document.getElementById("loginEmail")?.value.trim() || "";
        const resetInput = document.getElementById("resetPasswordEmailInput");
        if (resetInput && loginEmail) resetInput.value = loginEmail;
        const modal = new bootstrap.Modal(document.getElementById("forgotPasswordModal"));
        modal.show();
    });

    document.getElementById("confirmSendResetEmailBtn")?.addEventListener("click", function() {
        const email = document.getElementById("resetPasswordEmailInput")?.value.trim();
        const alertEl = document.getElementById("resetPasswordAlert");

        if (!email) {
            if (alertEl) {
                alertEl.innerText = "⚠️ Please enter your registered parent email address.";
                alertEl.className = "alert alert-warning extra-small py-2 px-3 mb-0";
                alertEl.classList.remove("d-none");
            }
            return;
        }

        if (typeof firebase !== "undefined" && firebase.auth) {
            firebase.auth().sendPasswordResetEmail(email)
                .then(() => {
                    if (alertEl) {
                        alertEl.innerText = `📧 Reset link sent to ${email}. Check your inbox!`;
                        alertEl.className = "alert alert-success extra-small py-2 px-3 mb-0";
                        alertEl.classList.remove("d-none");
                    }
                })
                .catch(err => {
                    if (alertEl) {
                        alertEl.innerText = `⚠️ Reset failed: ${err.message}`;
                        alertEl.className = "alert alert-danger extra-small py-2 px-3 mb-0";
                        alertEl.classList.remove("d-none");
                    }
                });
        }
    });

    // Safety Timeout: Transition to UNAAUTHENTICATED after 1.5s if Auth listener hasn't responded
    setTimeout(function() {
        if (currentAuthState === AUTH_STATES.INITIALIZING) {
            console.warn("[Firebase Auth] Auth state initialization timeout (1.5s). Displaying login screen.");
            setAuthState(AUTH_STATES.UNAUTHENTICATED);
        }
    }, 1500);

    // Initialize Authoritative Firebase Auth Listener
    if (typeof firebase !== "undefined" && firebase.auth) {
        try {
            firebase.auth().setPersistence(firebase.auth.Auth.Persistence.LOCAL).catch(err => {
                console.warn("[Firebase Auth] Persistence note:", err);
            });
        } catch (e) {
            console.warn("[Firebase Auth] setPersistence error:", e);
        }

        firebase.auth().onAuthStateChanged(function(user) {
            if (user) {
                setAuthState(AUTH_STATES.AUTHENTICATED, user);
            } else {
                setAuthState(AUTH_STATES.UNAUTHENTICATED);
            }
        });
    } else {
        setAuthState(AUTH_STATES.AUTHENTICATION_ERROR, "Firebase Authentication SDK failed to initialize. Please check network connection.");
    }
});

// Parent Registration Form Submission Handler
document.getElementById("signupForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const parentName = document.getElementById("signupName").value.trim();
    const email = document.getElementById("signupEmail").value.trim();
    const password = document.getElementById("signupPassword").value;
    const confirmPassword = document.getElementById("signupConfirmPassword").value;
    const termsChecked = document.getElementById("termsCheck").checked;
    const errorAlert = document.getElementById("authErrorAlert");

    if (!parentName || !email || !password) {
        if (errorAlert) {
            errorAlert.innerText = "⚠️ Please fill in all required parent account fields.";
            errorAlert.classList.remove("d-none");
        }
        return;
    }

    if (password !== confirmPassword) {
        if (errorAlert) {
            errorAlert.innerText = "⚠️ Password confirmation does not match.";
            errorAlert.classList.remove("d-none");
        }
        return;
    }

    if (!termsChecked) {
        if (errorAlert) {
            errorAlert.innerText = "⚠️ Please confirm terms as parent/guardian.";
            errorAlert.classList.remove("d-none");
        }
        return;
    }

    if (typeof firebase === "undefined" || !firebase.auth) {
        setAuthState(AUTH_STATES.AUTHENTICATION_ERROR, "Firebase Authentication SDK is not loaded.");
        return;
    }

    console.log(`[Firebase Auth] Creating new parent account for '${email}'...`);
    firebase.auth().createUserWithEmailAndPassword(email, password)
        .then(userCredential => {
            const user = userCredential.user;
            console.log("[Firebase Auth] Parent account created:", user.uid);

            if (user.updateProfile) {
                user.updateProfile({ displayName: parentName }).catch(_ => {});
            }

            if (user.sendEmailVerification) {
                user.sendEmailVerification().catch(_ => {});
            }

            // Display onboarding wizard for new parents
            showOnboardingWizard(parentName);
        })
        .catch(error => {
            console.warn("[Firebase Auth] Registration error:", error.code, error.message);
            if (errorAlert) {
                errorAlert.innerText = `Sign-up problem: ${error.message}`;
                errorAlert.classList.remove("d-none");
            }
        });
});

// Authoritative Login Form Submission Handler
document.getElementById("loginForm")?.addEventListener("submit", function(e) {
    e.preventDefault();
    const email = document.getElementById("loginEmail").value.trim();
    const password = document.getElementById("loginPassword").value.trim();
    const errorAlert = document.getElementById("authErrorAlert");

    if (!email || !password) {
        if (errorAlert) {
            errorAlert.innerText = "⚠️ Please enter both email address and password.";
            errorAlert.classList.remove("d-none");
        }
        return;
    }

    if (typeof firebase === "undefined" || !firebase.auth) {
        setAuthState(AUTH_STATES.AUTHENTICATION_ERROR, "Firebase Authentication SDK is not loaded.");
        return;
    }

    console.log(`[Firebase Auth] Authenticating parent account for '${email}'...`);
    firebase.auth().signInWithEmailAndPassword(email, password)
        .then(userCredential => {
            console.log("[Firebase Auth] Parent sign-in successful:", userCredential.user.uid);
        })
        .catch(error => {
            console.warn("[Firebase Auth] Sign-in error:", error.code, error.message);
            if (errorAlert) {
                const msg = error.code === "auth/wrong-password" || error.code === "auth/user-not-found" || error.code === "auth/invalid-credential"
                    ? "Sign-in problem: Incorrect email address or password."
                    : `Sign-in problem: ${error.message}`;
                errorAlert.innerText = `⚠️ ${msg}`;
                errorAlert.classList.remove("d-none");
            }
        });
});

// Authoritative Sign Out Handler
document.getElementById("logoutBtn")?.addEventListener("click", function() {
    console.log("[Firebase Auth] Initiating Parent Sign Out...");
    if (typeof firebase !== "undefined" && firebase.auth) {
        firebase.auth().signOut()
            .then(() => {
                setAuthState(AUTH_STATES.UNAUTHENTICATED);
            })
            .catch(err => {
                console.error("[Firebase Auth] Logout exception:", err);
                setAuthState(AUTH_STATES.UNAUTHENTICATED);
            });
    } else {
        setAuthState(AUTH_STATES.UNAUTHENTICATED);
    }
});

// First-Time Parent Guided Onboarding Wizard Engine
function showOnboardingWizard(parentName = "Parent") {
    onboardingCurrentStep = 1;
    updateOnboardingStepContent(parentName);
    const modal = new bootstrap.Modal(document.getElementById("onboardingWizardModal"));
    modal.show();
}

function updateOnboardingStepContent(parentName) {
    const content = document.getElementById("onboardingStepContent");
    const bar = document.getElementById("onboardingProgressBar");
    const prevBtn = document.getElementById("onboardingPrevBtn");
    const nextBtn = document.getElementById("onboardingNextBtn");

    if (!content || !bar) return;

    bar.style.width = (onboardingCurrentStep * 20) + "%";

    if (prevBtn) prevBtn.disabled = (onboardingCurrentStep === 1);
    if (nextBtn) nextBtn.innerText = (onboardingCurrentStep === 5) ? "Finish & Go to Dashboard" : "Continue";

    if (onboardingCurrentStep === 1) {
        content.innerHTML = `
            <div class="text-center py-3">
                <i class="fa-solid fa-shield-cat display-3 text-success mb-3"></i>
                <h4 class="fw-bold">Welcome, ${escapeHtml(parentName)}!</h4>
                <p class="small" style="color: var(--hm-text-secondary);">HomeMonitor helps you protect your family with simple, transparent device monitoring and location safety.</p>
                <div class="alert alert-info extra-small text-start py-2 px-3 mt-3">
                    <i class="fa-solid fa-circle-info me-1"></i>You will now be guided through pairing your child's first Android phone or tablet.
                </div>
            </div>
        `;
    } else if (onboardingCurrentStep === 2) {
        content.innerHTML = `
            <div class="py-2">
                <h5 class="fw-bold text-info mb-2"><i class="fa-solid fa-user-gear me-2"></i>Step 2: Parent Profile & Timezone</h5>
                <p class="small mb-3" style="color: var(--hm-text-secondary);">Confirm your dashboard notification preferences.</p>
                <div class="p-3 hm-card-subtle rounded border extra-small">
                    <p class="mb-1"><strong>Account:</strong> ${escapeHtml(currentUser?.email || parentName)}</p>
                    <p class="mb-0"><strong>Default Timezone:</strong> Auto (Local Browser Timezone)</p>
                </div>
            </div>
        `;
    } else if (onboardingCurrentStep === 3) {
        content.innerHTML = `
            <div class="py-2">
                <h5 class="fw-bold text-warning mb-2"><i class="fa-solid fa-mobile-screen me-2"></i>Step 3: Child Device Label</h5>
                <p class="small mb-3" style="color: var(--hm-text-secondary);">Give your child's device a recognizable name (e.g. Alex's Phone).</p>
                <input type="text" id="onboardingDeviceLabelInput" class="form-control form-control-hm mb-2" placeholder="Alex's Phone" value="Child's Phone">
            </div>
        `;
    } else if (onboardingCurrentStep === 4) {
        content.innerHTML = `
            <div class="text-center py-2">
                <h5 class="fw-bold text-success mb-2"><i class="fa-solid fa-qrcode me-2"></i>Step 4: Scan Pairing Code</h5>
                <p class="small mb-3" style="color: var(--hm-text-secondary);">Open HomeMonitor on your child's Android phone and scan this pairing code.</p>
                <div id="onboardingQrContainer" class="bg-white p-2 d-inline-block rounded shadow-sm mb-2" style="min-width: 160px; min-height: 160px;">
                    <div class="text-center py-3 text-muted"><i class="fa-solid fa-spinner fa-spin me-1"></i>Generating Local QR...</div>
                </div>
                <small class="extra-small d-block font-monospace text-primary fw-bold mt-1" id="onboardingTokenText">Generating 1-time token...</small>
                <small class="extra-small d-block" style="color: var(--hm-text-muted);">Pairing tokens expire after 5 minutes for security.</small>
            </div>
        `;
        if (typeof createOneTimeEnrollmentInvitation === "function") {
            createOneTimeEnrollmentInvitation(function(data) {
                if (data) {
                    const container = document.getElementById("onboardingQrContainer");
                    const txt = document.getElementById("onboardingTokenText");
                    if (container) {
                        let rendered = false;
                        if (typeof QRCode !== "undefined") {
                            try {
                                container.innerHTML = "";
                                new QRCode(container, { text: data.payload, width: 160, height: 160 });
                                if (container.querySelector("svg")) {
                                    rendered = true;
                                }
                            } catch (e) {
                                console.warn("[QR] Local SVG render notice, using fallback image:", e);
                            }
                        }
                        if (!rendered) {
                            container.innerHTML = `<img src="https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=${encodeURIComponent(data.payload)}" alt="Pairing Code QR" style="width: 160px; height: 160px;">`;
                        }
                    }
                    if (txt) txt.innerText = `Token: ${data.invitationId}`;
                }
            });
        }
    } else if (onboardingCurrentStep === 5) {
        content.innerHTML = `
            <div class="text-center py-3">
                <i class="fa-solid fa-circle-check display-3 text-success mb-3"></i>
                <h4 class="fw-bold">Setup Complete!</h4>
                <p class="small" style="color: var(--hm-text-secondary);">Your parent portal is ready. You can now monitor device location, screen time, and family activity.</p>
            </div>
        `;
    }
}

document.getElementById("onboardingNextBtn")?.addEventListener("click", function() {
    if (onboardingCurrentStep < 5) {
        onboardingCurrentStep++;
        updateOnboardingStepContent(currentUser?.displayName || "Parent");
    } else {
        const modalEl = document.getElementById("onboardingWizardModal");
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        if (typeof navigateToRoute === "function") {
            navigateToRoute("/dashboard", true);
        }
    }
});

document.getElementById("onboardingPrevBtn")?.addEventListener("click", function() {
    if (onboardingCurrentStep > 1) {
        onboardingCurrentStep--;
        updateOnboardingStepContent(currentUser?.displayName || "Parent");
    }
});

function generateSecureRandomHex(bytes = 16) {
    const array = new Uint8Array(bytes);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
}

function createOneTimeEnrollmentInvitation(callback) {
    const parentUid = (currentUser && currentUser.uid) ? currentUser.uid : "parent_default";
    const invitationId = "inv_" + generateSecureRandomHex(16);
    const expiresAt = Date.now() + 5 * 60 * 1000; // 5 minutes expiration

    const payload = JSON.stringify({
        v: 2,
        project: "homemonitor-db",
        invitationId: invitationId,
        parentUid: parentUid,
        expiresAt: expiresAt
    });

    const invData = {
        invitationId: invitationId,
        parentUid: parentUid,
        createdAt: (typeof firebase !== "undefined" && firebase.database) ? firebase.database.ServerValue.TIMESTAMP : Date.now(),
        expiresAt: expiresAt,
        status: "PENDING"
    };

    // Invoke callback IMMEDIATELY (0 ms latency) so token and QR render on screen instantly
    if (callback) callback({ invitationId, expiresAt, payload });

    // Sync invitation asynchronously to Firebase Realtime Database
    if (typeof firebaseDatabase !== "undefined" && firebaseDatabase && currentUser && currentUser.uid) {
        firebaseDatabase.ref(`enrollment_invitations/${invitationId}`).set(invData)
            .then(() => {
                console.log(`[Enrollment] Created 1-time invitation in RTDB: ${invitationId}`);
            })
            .catch(err => {
                console.warn("[Enrollment] RTDB invitation sync notice:", err);
            });
    }
}
