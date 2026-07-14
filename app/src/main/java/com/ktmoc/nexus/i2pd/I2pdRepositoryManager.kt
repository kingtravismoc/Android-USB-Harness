package com.ktmoc.nexus.i2pd

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * I2P Daemon (i2pd) Repository Manager
 * Handles pushing updates from GitHub to I2P eepsite for F-Droid distribution
 */
class I2pdRepositoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "I2pdRepoManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/ktmoc/nexus/releases/latest"
        private const val FDROID_INDEX_PATH = "/fdroid/repo/index.xml"
        private const val FDROID_ARCHIVE_PATH = "/fdroid/repo/"
    }
    
    private val prefs = context.getSharedPreferences("i2pd_repo_prefs", Context.MODE_PRIVATE)
    private var syncJob: Job? = null
    
    data class RepoConfig(
        val i2pDestination: String,
        val eepsitePath: String,
        val repoUrl: String,
        val checkIntervalHours: Int = 6
    )
    
    /**
     * Initialize I2P repository configuration
     */
    fun initializeConfig(config: RepoConfig) {
        prefs.edit().apply {
            putString("i2p_destination", config.i2pDestination)
            putString("eepsite_path", config.eepsitePath)
            putString("repo_url", config.repoUrl)
            putInt("check_interval_hours", config.checkIntervalHours)
            apply()
        }
        Log.i(TAG, "I2P repository initialized: ${config.repoUrl}")
    }
    
    /**
     * Start automatic synchronization from GitHub to I2P eepsite
     */
    fun startAutoSync() {
        syncJob?.cancel()
        val intervalHours = prefs.getInt("check_interval_hours", 6)
        
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                syncFromGitHub()
                delay(intervalHours * 60 * 60 * 1000L)
            }
        }
        Log.d(TAG, "Started auto-sync every $intervalHours hours")
    }
    
    /**
     * Stop automatic synchronization
     */
    fun stopAutoSync() {
        syncJob?.cancel()
        syncJob = null
    }
    
    /**
     * Force synchronization from GitHub to I2P eepsite
     */
    suspend fun forceSync(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting forced sync from GitHub to I2P...")
                
                // Step 1: Fetch latest release info from GitHub
                val releaseInfo = fetchGitHubRelease()
                    ?: return@withContext Result.failure(Exception("Failed to fetch GitHub release"))
                
                Log.d(TAG, "Fetched GitHub release: ${releaseInfo.versionName}")
                
                // Step 2: Download APK and metadata
                val downloadResult = downloadReleaseAssets(releaseInfo)
                    ?: return@withContext Result.failure(Exception("Failed to download release assets"))
                
                Log.d(TAG, "Downloaded release assets")
                
                // Step 3: Update F-Droid index
                val indexUpdated = updateFdroidIndex(releaseInfo, downloadResult.apkHash)
                    ?: return@withContext Result.failure(Exception("Failed to update F-Droid index"))
                
                Log.d(TAG, "Updated F-Droid index")
                
                // Step 4: Deploy to I2P eepsite
                val deployed = deployToEepsite(downloadResult.apkPath, downloadResult.metadataPath, indexUpdated)
                
                if (deployed) {
                    Log.i(TAG, "✅ Successfully synced to I2P eepsite")
                    saveLastSyncTime()
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to deploy to eepsite"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Force sync failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Automatic sync from GitHub (called periodically)
     */
    private suspend fun syncFromGitHub() {
        val lastSync = prefs.getLong("last_sync_time", 0)
        val now = System.currentTimeMillis()
        val intervalHours = prefs.getInt("check_interval_hours", 6)
        
        if (now - lastSync < intervalHours * 60 * 60 * 1000L) {
            Log.d(TAG, "Skipping sync - too soon since last sync")
            return
        }
        
        forceSync().onSuccess {
            Log.i(TAG, "Automatic sync completed successfully")
        }.onFailure { error ->
            Log.e(TAG, "Automatic sync failed: ${error.message}")
        }
    }
    
    /**
     * Fetch latest release information from GitHub
     */
    private suspend fun fetchGitHubRelease(): GitHubRelease? {
        return try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                GitHubRelease(
                    versionName = json.getString("tag_name"),
                    versionCode = json.optInt("version_code", 1),
                    apkUrl = json.getJSONObject("assets").getJSONArray("apk")?.optJSONObject(0)?.getString("browser_download_url") ?: "",
                    releaseNotes = json.optString("body", ""),
                    isCritical = json.optString("body", "").contains("[CRITICAL]", ignoreCase = true)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch GitHub release", e)
            null
        }
    }
    
    data class GitHubRelease(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val releaseNotes: String,
        val isCritical: Boolean
    )
    
    data class DownloadResult(
        val apkPath: String,
        val metadataPath: String,
        val apkHash: String
    )
    
    /**
     * Download release assets (APK and metadata)
     */
    private suspend fun downloadReleaseAssets(release: GitHubRelease): DownloadResult? {
        return try {
            val cacheDir = File(context.cacheDir, "fdroid_downloads")
            cacheDir.mkdirs()
            
            // Download APK
            val apkFile = File(cacheDir, "nexus-${release.versionName}.apk")
            downloadFile(release.apkUrl, apkFile)
            
            // Calculate SHA256 hash
            val apkHash = calculateSha256(apkFile)
            
            // Create metadata file
            val metadataFile = File(cacheDir, "metadata-${release.versionName}.xml")
            createMetadataFile(metadataFile, release, apkHash)
            
            DownloadResult(
                apkPath = apkFile.absolutePath,
                metadataPath = metadataFile.absolutePath,
                apkHash = apkHash
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download release assets", e)
            null
        }
    }
    
    /**
     * Download file from URL
     */
    private suspend fun downloadFile(urlString: String, destination: File) {
        withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    
    /**
     * Calculate SHA256 hash of file
     */
    private fun calculateSha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Create F-Droid metadata XML file
     */
    private fun createMetadataFile(file: File, release: GitHubRelease, apkHash: String) {
        val metadata = """<?xml version="1.0" encoding="utf-8"?>
<metadata>
    <id>com.ktmoc.nexus</id>
    <name>KTMOC NEXUS</name>
    <summary>AI-Powered WebUSB Debugging Harness</summary>
    <description>$${release.releaseNotes}</description>
    <packages>
        <package versionCode="${release.versionCode}" versionName="${release.versionName}" apk="nexus-${release.versionName}.apk" sig="signature_hash">
            <hash type="sha256">$apkHash</hash>
        </package>
    </packages>
</metadata>"""
        
        file.writeText(metadata)
    }
    
    /**
     * Update F-Droid index.xml with new release
     */
    private suspend fun updateFdroidIndex(release: GitHubRelease, apkHash: String): String? {
        return try {
            val eepsitePath = prefs.getString("eepsite_path", "/var/lib/i2pd/eepsites/ktmocnexus")
            val indexPath = File(eepsitePath, FDROID_INDEX_PATH)
            indexPath.parentFile?.mkdirs()
            
            val indexContent = """<?xml version="1.0" encoding="utf-8"?>
<index>
    <name>KTMOC NEXUS Official Repository</name>
    <description>Official F-Droid repository for KTMOC NEXUS</description>
    <timestamp>${System.currentTimeMillis()}</timestamp>
    <packages>
        <package id="com.ktmoc.nexus">
            <name>KTMOC NEXUS</name>
            <version code="${release.versionCode}" name="${release.versionName}">
                <apk file="nexus-${release.versionName}.apk" hash="$apkHash" />
            </version>
        </package>
    </packages>
</index>"""
            
            indexPath.writeText(indexContent)
            indexContent
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update F-Droid index", e)
            null
        }
    }
    
    /**
     * Deploy files to I2P eepsite
     */
    private suspend fun deployToEepsite(apkPath: String, metadataPath: String, indexContent: String): Boolean {
        return try {
            val eepsitePath = prefs.getString("eepsite_path", "/var/lib/i2pd/eepsites/ktmocnexus")
            val repoDir = File(eepsitePath, FDROID_ARCHIVE_PATH)
            repoDir.mkdirs()
            
            // Copy APK to eepsite
            File(apkPath).copyTo(File(repoDir, File(apkPath).name), overwrite = true)
            
            // Copy metadata to eepsite
            File(metadataPath).copyTo(File(repoDir, File(metadataPath).name), overwrite = true)
            
            Log.i(TAG, "Deployed to eepsite: $eepsitePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy to eepsite", e)
            false
        }
    }
    
    /**
     * Save last sync timestamp
     */
    private fun saveLastSyncTime() {
        prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
    }
    
    /**
     * Get last sync information
     */
    fun getLastSyncInfo(): SyncInfo? {
        return try {
            SyncInfo(
                lastSyncTime = prefs.getLong("last_sync_time", 0),
                i2pDestination = prefs.getString("i2p_destination", "") ?: "",
                repoUrl = prefs.getString("repo_url", "") ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class SyncInfo(
        val lastSyncTime: Long,
        val i2pDestination: String,
        val repoUrl: String
    )
}
