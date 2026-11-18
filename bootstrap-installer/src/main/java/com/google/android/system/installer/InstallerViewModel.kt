package com.google.android.system.installer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class InstallerViewModel(application: Application) : AndroidViewModel(application) {

    enum class InstallerState {
        NOT_STARTED,
        CHECKING_CONFIGURATION,
        CHECKING_PERMISSION,
        DOWNLOADING,
        VERIFYING_APK,
        READY_TO_INSTALL,
        WAITING_FOR_USER_APPROVAL,
        INSTALLING,
        INSTALL_SUCCESS,
        INSTALL_FAILURE,
        REQUESTING_SELF_UNINSTALL,
        SELF_UNINSTALL_CANCELLED
    }

    private val _state = MutableStateFlow(InstallerState.NOT_STARTED)
    val state: StateFlow<InstallerState> = _state.asStateFlow()

    private val _progressPercent = MutableStateFlow(0)
    val progressPercent: StateFlow<Int> = _progressPercent.asStateFlow()

    private val _downloadStatusText = MutableStateFlow("")
    val downloadStatusText: StateFlow<String> = _downloadStatusText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isMainAppAlreadyInstalled = MutableStateFlow(false)
    val isMainAppAlreadyInstalled: StateFlow<Boolean> = _isMainAppAlreadyInstalled.asStateFlow()

    private val downloader = ApkDownloader(application)
    private val validator = ApkValidator(application)
    private val packageInstallerHelper = PackageInstallerHelper(application)

    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null
    private var installStatusReceiver: BroadcastReceiver? = null

    init {
        checkMainAppInstallationStatus()
        registerInstallReceiver()
    }

    fun checkMainAppInstallationStatus() {
        val pm = getApplication<Application>().packageManager
        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(InstallerConfig.MAIN_APP_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(InstallerConfig.MAIN_APP_PACKAGE_NAME, 0)
            }
            _isMainAppAlreadyInstalled.value = pkgInfo != null
        } catch (e: Exception) {
            _isMainAppAlreadyInstalled.value = false
        }
    }

    fun startInstallationFlow() {
        if (!InstallerConfig.isConfigValid()) {
            _errorMessage.value = "Configuration error: MAIN_APP_APK_URL contains invalid placeholder or is not HTTPS."
            _state.value = InstallerState.INSTALL_FAILURE
            return
        }

        if (!packageInstallerHelper.canRequestPackageInstalls()) {
            _state.value = InstallerState.CHECKING_PERMISSION
            return
        }

        downloadAndVerify()
    }

    fun onPermissionGranted() {
        downloadAndVerify()
    }

    fun downloadAndVerify() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = InstallerState.DOWNLOADING
                _downloadStatusText.value = "Connecting..."
                _progressPercent.value = 0

                val apkFile = downloader.downloadApk(
                    urlString = InstallerConfig.MAIN_APP_APK_URL,
                    onProgress = { progress ->
                        _progressPercent.value = progress.progressPercent
                        _downloadStatusText.value = if (progress.totalBytes > 0) {
                            "${progress.bytesRead / 1024 / 1024} MB / ${progress.totalBytes / 1024 / 1024} MB (${progress.progressPercent}%)"
                        } else {
                            "${progress.bytesRead / 1024 / 1024} MB downloaded"
                        }
                    },
                    isCancelled = { false }
                )

                downloadedApkFile = apkFile
                _state.value = InstallerState.VERIFYING_APK
                _downloadStatusText.value = "Verifying APK..."

                val validation = validator.validateApk(apkFile)
                if (!validation.isValid) {
                    _errorMessage.value = validation.errorMessage ?: "APK validation failed."
                    _state.value = InstallerState.INSTALL_FAILURE
                    return@launch
                }

                _state.value = InstallerState.READY_TO_INSTALL
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unknown download or verification error."
                _state.value = InstallerState.INSTALL_FAILURE
            }
        }
    }

    fun proceedToSystemInstall() {
        val file = downloadedApkFile
        if (file == null || !file.exists()) {
            _errorMessage.value = "Downloaded APK file not found."
            _state.value = InstallerState.INSTALL_FAILURE
            return
        }

        try {
            _state.value = InstallerState.WAITING_FOR_USER_APPROVAL
            packageInstallerHelper.installApk(file)
            _state.value = InstallerState.INSTALLING
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Failed to launch package installer."
            _state.value = InstallerState.INSTALL_FAILURE
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloader.cleanup()
        _state.value = InstallerState.NOT_STARTED
    }

    private fun registerInstallReceiver() {
        val context = getApplication<Application>()
        installStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == PackageInstallerHelper.ACTION_INSTALL_COMPLETE) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(Intent.EXTRA_INTENT)
                            }
                            confirmIntent?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(it)
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            _state.value = InstallerState.INSTALL_SUCCESS
                            checkMainAppInstallationStatus()
                        }
                        PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_ABORTED -> {
                            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation aborted or failed."
                            _errorMessage.value = message
                            _state.value = InstallerState.INSTALL_FAILURE
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(PackageInstallerHelper.ACTION_INSTALL_COMPLETE)
        ContextCompat.registerReceiver(
            context,
            installStatusReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun triggerSelfUninstall() {
        _state.value = InstallerState.REQUESTING_SELF_UNINSTALL
        SelfUninstallHelper.requestSelfUninstall(getApplication())
    }

    fun openMainApp() {
        val pm = getApplication<Application>().packageManager
        val launchIntent = pm.getLaunchIntentForPackage(InstallerConfig.MAIN_APP_PACKAGE_NAME)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(launchIntent)
        } else {
            _errorMessage.value = "Main app launch intent not found."
        }
    }

    override fun onCleared() {
        super.onCleared()
        installStatusReceiver?.let {
            try {
                getApplication<Application>().unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        downloader.cleanup()
    }
}
