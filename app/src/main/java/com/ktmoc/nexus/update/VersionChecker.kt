package com.ktmoc.nexus.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Version Checker and Update Manager for F-Droid Repository
 * Ensures genuine installations by verifying against GitHub source
 * Automatically checks every 6 hours for updates
 */
class VersionChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "VersionChecker"
        private const val GITHUB_API_URL = "https://api.github.com/repos/ktmoc/nexus/releases/latest"
        private const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/ktmoc/nexus/main/version.json"
        private const val MIN_VERSION_CODE = 1 // First push version code
        private const val CHECK_INTERVAL_HOURS = 6
        private const val PREFS_NAME = "version_check_prefs"
        
        fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var updateCheckJob: Job? = null
    
    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val sha256Hash: String,
        val releaseNotes: String,
        val isCritical: Boolean,
        val minRequiredVersion: Int
    )
    
    /**
     * Start periodic version checking (every 6 hours)
     */
    fun startPeriodicCheck() {
        updateCheckJob?.cancel()
        updateCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                checkForUpdates()
                delay(CHECK_INTERVAL_HOURS * 60 * 60 * 1000L)
            }
        }
        Log.d(TAG, "Started periodic version check every $CHECK_INTERVAL_HOURS hours")
    }
    
    /**
     * Stop periodic checking
     */
    fun stopPeriodicCheck() {
        updateCheckJob?.cancel()
        updateCheckJob = null
    }
    
    /**
     * Force immediate update check from GitHub
     */
    suspend fun forceUpdateCheck(): Result<VersionInfo?> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_RAW_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val versionInfo = VersionInfo(
                        versionCode = json.getInt("versionCode"),
                        versionName = json.getString("versionName"),
                        sha256Hash = json.getString("sha256Hash"),
                        releaseNotes = json.optString("releaseNotes", ""),
                        isCritical = json.optBoolean("isCritical", false),
                        minRequiredVersion = json.optInt("minRequiredVersion", MIN_VERSION_CODE)
                    )
                    
                    Log.d(TAG, "Fetched version info: ${versionInfo.versionName}")
                    saveLastCheckTime()
                    Result.success(versionInfo)
                } else {
                    Log.e(TAG, "GitHub API error: $responseCode")
                    Result.failure(Exception("GitHub API error: $responseCode"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Force update check failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check for updates (called periodically)
     */
    private suspend fun checkForUpdates() {
        val lastCheck = prefs.getLong("last_check_time", 0)
        val now = System.currentTimeMillis()
        
        if (now - lastCheck < CHECK_INTERVAL_HOURS * 60 * 60 * 1000L) {
            Log.d(TAG, "Skipping check - too soon since last check")
            return
        }
        
        forceUpdateCheck().onSuccess { versionInfo ->
            versionInfo?.let {
                verifyAndNotifyUpdate(it)
            }
        }
    }
    
    /**
     * Verify current installation against GitHub version
     */
    fun verifyInstallation(currentVersionCode: Int, currentApkHash: String): Boolean {
        val storedMinVersion = prefs.getInt("min_required_version", MIN_VERSION_CODE)
        
        // Check if current version meets minimum requirements
        if (currentVersionCode < storedMinVersion) {
            Log.e(TAG, "Installation verification failed - version too old: $currentVersionCode < $storedMinVersion")
            return false
        }
        
        // Verify APK hash if we have a stored expected hash
        val expectedHash = prefs.getString("expected_sha256", null)
        if (expectedHash != null && currentApkHash.isNotEmpty()) {
            if (!currentApkHash.equals(expectedHash, ignoreCase = true)) {
                Log.e(TAG, "Installation verification failed - APK hash mismatch")
                return false
            }
        }
        
        Log.d(TAG, "Installation verification passed")
        return true
    }
    
    /**
     * Verify and notify about available update
     */
    private fun verifyAndNotifyUpdate(versionInfo: VersionInfo) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = packageInfo.versionCode
        
        prefs.edit().apply {
            putInt("latest_version_code", versionInfo.versionCode)
            putString("latest_version_name", versionInfo.versionName)
            putString("latest_release_notes", versionInfo.releaseNotes)
            putBoolean("is_critical_update", versionInfo.isCritical)
            putInt("min_required_version", versionInfo.minRequiredVersion)
            putString("expected_sha256", versionInfo.sha256Hash)
            apply()
        }
        
        val needsUpdate = versionInfo.versionCode > currentVersionCode
        val isGenuine = verifyInstallation(currentVersionCode, "")
        
        if (!isGenuine) {
            Log.e(TAG, "⚠️ CRITICAL: Installation appears to be tampered or outdated!")
            // Trigger critical security notification
            notifyCriticalSecurityAlert(versionInfo)
        } else if (needsUpdate) {
            Log.i(TAG, "Update available: ${versionInfo.versionName}")
            if (versionInfo.isCritical) {
                notifyCriticalUpdate(versionInfo)
            } else {
                notifyRegularUpdate(versionInfo)
            }
        } else {
            Log.d(TAG, "Already on latest version")
        }
    }
    
    /**
     * Notify user of critical security update
     */
    private fun notifyCriticalUpdate(versionInfo: VersionInfo) {
        Log.wtf(TAG, "🚨 CRITICAL SECURITY UPDATE AVAILABLE: ${versionInfo.versionName}")
        Log.wtf(TAG, "Reason: ${versionInfo.releaseNotes}")
        // In production: Show persistent notification, block app usage until updated
    }
    
    /**
     * Notify user of regular update
     */
    private fun notifyRegularUpdate(versionInfo: VersionInfo) {
        Log.i(TAG, "📦 Update available: ${versionInfo.versionName}")
        Log.i(TAG, "Changes: ${versionInfo.releaseNotes}")
        // In production: Show standard update notification
    }
    
    /**
     * Notify user of critical security alert (tampered installation)
     */
    private fun notifyCriticalSecurityAlert(versionInfo: VersionInfo) {
        Log.wtf(TAG, "🛑 SECURITY ALERT: Installation may be compromised!")
        Log.wtf(TAG, "Minimum required version: ${versionInfo.minRequiredVersion}")
        Log.wtf(TAG, "Please download genuine version from official I2P repository")
        // In production: Block app functionality, show warning screen
    }
    
    /**
     * Save last check timestamp
     */
    private fun saveLastCheckTime() {
        prefs.edit().putLong("last_check_time", System.currentTimeMillis()).apply()
    }
    
    /**
     * Get last checked version info
     */
    fun getLastCheckedVersion(): VersionInfo? {
        return try {
            VersionInfo(
                versionCode = prefs.getInt("latest_version_code", 0),
                versionName = prefs.getString("latest_version_name", "") ?: "",
                sha256Hash = prefs.getString("expected_sha256", "") ?: "",
                releaseNotes = prefs.getString("latest_release_notes", "") ?: "",
                isCritical = prefs.getBoolean("is_critical_update", false),
                minRequiredVersion = prefs.getInt("min_required_version", MIN_VERSION_CODE)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate SHA256 hash of APK file
     */
    fun calculateApkHash(apkPath: String): String {
        return try {
            val file = java.io.File(apkPath)
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(md.digest())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate APK hash", e)
            ""
        }
    }
}
