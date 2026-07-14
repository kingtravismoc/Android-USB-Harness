# KTMOC NEXUS F-Droid Repository Guide

## Overview

This document describes the complete F-Droid compatible repository system for KTMOC NEXUS, featuring:

- **Automatic version verification** against GitHub source
- **6-hour update checks** with force update capability
- **I2P/i2pd eepsite deployment** for anonymous distribution
- **SHA256 integrity verification** for all APKs
- **Critical security hot-patching** system
- **Minimum version enforcement** to prevent outdated installations

## Architecture

```
GitHub Repository
       │
       ▼ (Push/Tag)
GitHub Actions (Every 6 hours or on push)
       │
       ├── Build APK
       ├── Calculate SHA256
       ├── Generate version.json
       └── Deploy to I2P Eepsite
               │
               ▼
       F-Droid Repository (index.xml + metadata)
               │
               ▼
       KTMOC NEXUS App (VersionChecker)
               │
               ├── Verifies installation
               ├── Checks minimum version
               └── Alerts on tampering
```

## Components

### 1. Version Checker (`VersionChecker.kt`)

Located in: `app/src/main/java/com/ktmoc/nexus/update/VersionChecker.kt`

**Features:**
- Periodic checking every 6 hours
- Force update from GitHub
- SHA256 hash verification
- Minimum version enforcement (starts at version code 1)
- Critical security alerts

**Usage in App:**
```kotlin
val versionChecker = VersionChecker(context)
versionChecker.startPeriodicCheck()

// Force check
val result = versionChecker.forceUpdateCheck()
result.onSuccess { info ->
    if (info.isCritical) {
        // Block app usage until updated
    }
}

// Verify installation
val isGenuine = versionChecker.verifyInstallation(
    currentVersionCode = 1,
    currentApkHash = "sha256_hash_here"
)
```

### 2. I2P Repository Manager (`I2pdRepositoryManager.kt`)

Located in: `app/src/main/java/com/ktmoc/nexus/i2pd/I2pdRepositoryManager.kt`

**Features:**
- Sync from GitHub to I2P eepsite
- Automatic deployment every 6 hours
- F-Droid index.xml generation
- Metadata creation

**Configuration:**
```kotlin
val config = I2pdRepositoryManager.RepoConfig(
    i2pDestination = "YOUR_I2P_DESTINATION",
    eepsitePath = "/var/lib/i2pd/eepsites/ktmocnexus",
    repoUrl = "https://ktmocnexus.i2p/fdroid/repo",
    checkIntervalHours = 6
)

val repoManager = I2pdRepositoryManager(context)
repoManager.initializeConfig(config)
repoManager.startAutoSync()
```

### 3. GitHub Actions Workflow

Located in: `.github/workflows/fdroid-build-deploy.yml`

**Triggers:**
- Push to main/master branches
- Tag creation (v*)
- Scheduled every 6 hours
- Manual dispatch with force deploy option

**Jobs:**
1. **build**: Compile APK, calculate hash, create version.json
2. **verify**: Validate version structure and minimum requirements
3. **deploy-i2p**: Deploy to I2P eepsite
4. **security-check**: Scan for dangerous permissions and F-Droid compliance

### 4. F-Droid Repository Tool

Located in: `tools/fdroid/fdroid_repo_tool.py`

**Commands:**
```bash
# Initialize repository
python tools/fdroid/fdroid_repo_tool.py init

# Build APK
python tools/fdroid/fdroid_repo_tool.py build

# Generate metadata
python tools/fdroid/fdroid_repo_tool.py metadata

# Update index
python tools/fdroid/fdroid_repo_tool.py index

# Sign repository
python tools/fdroid/fdroid_repo_tool.py sign

# Deploy to I2P
python tools/fdroid/fdroid_repo_tool.py deploy

# Sync from GitHub
python tools/fdroid/fdroid_repo_tool.py sync

# Verify installation
python tools/fdroid/fdroid_repo_tool.py verify path/to/apk.apk

# Run complete pipeline
python tools/fdroid/fdroid_repo_tool.py all
```

### 5. Version Configuration

Located in: `version.json`

```json
{
  "versionCode": 1,
  "versionName": "1.0.0",
  "sha256Hash": "CALCULATED_BY_CI",
  "releaseNotes": "Release description",
  "isCritical": false,
  "minRequiredVersion": 1,
  "timestamp": 0,
  "githubRepo": "ktmoc/nexus",
  "fdroidRepoUrl": "https://ktmocnexus.i2p/fdroid/repo",
  "i2pDestination": "YOUR_I2P_DESTINATION"
}
```

## Setup Instructions

### 1. Initialize Repository

```bash
# Create repository structure
python tools/fdroid/fdroid_repo_tool.py init

# Configure settings
# Edit fdroid-repo/config.yml with your I2P details
```

### 2. Configure GitHub Secrets

In your GitHub repository settings, add:
- `I2P_DESTINATION`: Your I2P destination key
- `KESTORE_PASSWORD`: Signing keystore password (if using signed releases)

### 3. Build First Release

```bash
# Build and deploy initial version
./gradlew assembleRelease
python tools/fdroid/fdroid_repo_tool.py all
```

### 4. Deploy to I2P

```bash
# Copy repository to I2P eepsite
python tools/fdroid/fdroid_repo_tool.py deploy

# Or use GitHub Actions with workflow_dispatch
# Set force_deploy: true
```

### 5. Add Repository to F-Droid Client

On Android device with F-Droid:
1. Open F-Droid app
2. Settings → Repositories
3. Add repository: `https://ktmocnexus.i2p/fdroid/repo`
4. Enable repository
5. Install KTMOC NEXUS

## Security Features

### Version Verification

The app verifies its own installation on startup:

```kotlin
// In Application class or MainActivity
override fun onCreate() {
    super.onCreate()
    
    val versionChecker = VersionChecker(this)
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val currentVersion = packageInfo.versionCode
    
    // Calculate APK hash
    val apkPath = applicationInfo.sourceDir
    val apkHash = versionChecker.calculateApkHash(apkPath)
    
    // Verify authenticity
    if (!versionChecker.verifyInstallation(currentVersion, apkHash)) {
        // Show security warning
        // Block app functionality
        showSecurityAlert()
    }
    
    // Start periodic checks
    versionChecker.startPeriodicCheck()
}
```

### Critical Updates

When a critical security issue is discovered:

1. Update `version.json` with:
   ```json
   {
     "isCritical": true,
     "minRequiredVersion": 2,
     "releaseNotes": "[CRITICAL] Security fix for vulnerability XYZ"
   }
   ```

2. Push to GitHub (triggers automatic deployment)

3. All installed apps will:
   - Detect critical update on next 6-hour check
   - Block functionality until updated
   - Display security warning to user

### Hot Patching

For urgent security fixes:

```bash
# Create hotfix branch
git checkout -b hotfix/security-fix

# Apply fix and increment version
# Update version.json with minRequiredVersion

# Tag and push
git tag v1.0.1-hotfix
git push origin hotfix/security-fix --tags

# GitHub Actions automatically deploys
# Force deploy enabled for immediate effect
```

## I2P Integration

### i2pd Configuration

Edit `/var/lib/i2pd/i2pd.conf`:

```ini
[http]
enabled=true
port=8080
address=127.0.0.1
webdir=/var/lib/i2pd/eepsites/ktmocnexus

[tunnel]
hidden=ktmocnexus
type=website
localPort=8080
localHost=127.0.0.1
```

### Eepsite Structure

```
/var/lib/i2pd/eepsites/ktmocnexus/
├── fdroid/
│   └── repo/
│       ├── index.xml
│       ├── nexus-1.0.0.apk
│       └── com.ktmoc.nexus.xml
└── index.html
```

## Troubleshooting

### Version Check Fails

If installation verification fails:
1. Ensure `version.json` has correct `minRequiredVersion`
2. Verify APK hash matches `sha256Hash` in version.json
3. Check that version code >= minimum required

### I2P Deployment Issues

If deployment to I2P fails:
1. Verify eepsite directory exists and is writable
2. Check i2pd service is running
3. Ensure correct permissions on eepsite files

### GitHub Actions Failures

Common issues:
- Missing secrets configuration
- Gradle build errors
- APK not found after build
- Permission denied on deployment

Check workflow logs for specific error messages.

## Best Practices

1. **Always increment versionCode** for each release
2. **Set minRequiredVersion carefully** - too high blocks legitimate users
3. **Use isCritical sparingly** - only for genuine security issues
4. **Test updates** before deploying to production
5. **Monitor GitHub Actions** for deployment failures
6. **Keep backups** of signing keys and configuration

## License

This system is released under GPL-3.0-or-later, compatible with F-Droid requirements.
