# KTMOC NEXUS - Quick Start Guide

![Quick Start](https://i.imgur.com/placeholder_quickstart.png)

> **WATERMARKED FOR KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU**  
> *Sole Launcher: [@kingtravismoc](https://github.com/kingtravismoc)*

---

## 🚀 5-Minute Setup

### Step 1: Clone Repository

```bash
git clone https://github.com/kingtravismoc/ktmoc-nexus.git
cd ktmoc-nexus
```

### Step 2: Generate Encryption Secret

```bash
# This secret encrypts all user-generated methods
export ENCRYPTION_SECRET=$(openssl rand -hex 32)
echo "ENCRYPTION_SECRET=$ENCRYPTION_SECRET" >> .env
```

### Step 3: Configure GitHub Secrets

Go to: `https://github.com/kingtravismoc/ktmoc-nexus/settings/secrets/actions`

Add these secrets:

| Name | Value |
|------|-------|
| `SOLE_LAUNCHER_USERNAME` | `kingtravismoc` |
| `ENCRYPTION_SECRET` | Output from step 2 |
| `ANDROID_RELEASE_KEYSTORE` | `base64 keystore.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Your password |
| `ANDROID_KEY_ALIAS` | Your alias |
| `ANDROID_KEY_PASSWORD` | Your key password |

### Step 4: Generate GPG Keys (If you don't have them)

```bash
# Generate new GPG key for signing commits
gpg --full-generate-key
# Use email associated with GitHub account

# Export public key for GitHub
gpg --armor --export your-email@example.com
# Add to GitHub: Settings → SSH and GPG keys → New GPG key

# Get key ID
gpg --fingerprint your-email@example.com
# Save the KEY_ID (last 16 characters)

# Export private key for repo signing (keep secure!)
gpg --armor --export-secret-keys your-email@example.com > repo-private.key
```

### Step 5: Sign Your First Commit

```bash
# Configure git to use your GPG key
git config --global user.signingkey YOUR_KEY_ID
git config --global commit.gpgsign true

# Make a test commit
echo "# Test" >> TEST.md
git add TEST.md
git commit -S -m "Initial signed commit"

# Verify signature
git log --show-signature -1
```

### Step 6: Push to Trigger Build

```bash
git push origin main
```

GitHub Actions will now:
1. ✅ Verify your GPG signature
2. ✅ Confirm you're @kingtravismoc
3. ✅ Build signed APK
4. ✅ Generate version.json
5. ✅ Deploy to I2PD (if configured)

---

## 💻 Web Console Quick Start

### Install Dependencies

```bash
cd tools/web_console
pip install flask requests python-gnupg
```

### Run Console

```bash
# Set required environment variables
export GITHUB_REPO="kingtravismoc/ktmoc-nexus"
export ENCRYPTION_SECRET="$(cat ../../.env | grep ENCRYPTION_SECRET | cut -d'=' -f2)"

# Start server
python web_console.py
```

### Access Dashboard

Open browser: `http://localhost:5000`

You'll see:
- Repository status
- I2PD deployment controls
- Method management (greenlight/block)
- User banning system
- AI code generator
- Real-time logs

---

## 📱 Build & Install Android App

### Debug Build (Testing)

```bash
cd /workspace
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (Production)

```bash
# Requires keystore configuration in local.properties
./gradlew assembleRelease

# Install on device
adb install app/build/outputs/apk/release/app-release.apk
```

### Verify Installation

Open app on Android device:
- Should show CRT interface
- Check "Settings → About" for version
- Verify watermark: "KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU"

---

## 🔧 Configure I2PD (Optional)

### Install i2pd

```bash
# Ubuntu/Debian
sudo apt install i2pd

# Enable service
sudo systemctl enable i2pd
sudo systemctl start i2pd
```

### Create Eepsite Tunnel

Edit `/etc/i2pd/tunnels.conf`:

```ini
[ktmoc-nexus]
type = http
host = 127.0.0.1
port = 5000
destination = your_destination.b32.i2p
inbound.length = 3
outbound.length = 3
```

### Get Destination Address

```bash
# After restarting i2pd, find your destination
cat /var/lib/i2pd/your_destination.txt
# Copy the .b32.i2p address
```

### Add to GitHub Secrets

| Secret | Value |
|--------|-------|
| `I2PD_EEPSITE_DESTINATION` | Your .b32.i2p address |
| `I2PD_API_KEY` | From i2pd admin panel |

---

## 🤖 Test AI Features

### In Web Console

1. Navigate to "AI ASSISTANT" section
2. Enter task: `"Create shim for OpenWeather API"`
3. Click "🤖 Generate with AI"
4. Review generated code
5. Click "✅ Greenlight" to approve

### In Android App

1. Open app
2. Go to "NEXUS CHAT"
3. Type: `/shim OpenWeather`
4. AI generates integration code
5. Code stored encrypted to I2PD

---

## 🔐 Security Checklist

- [ ] GPG key generated and added to GitHub
- [ ] All commits signed with `-S` flag
- [ ] Android keystore created and backed up
- [ ] Encryption secret generated and stored
- [ ] GitHub secrets configured
- [ ] Region lock set (if needed)
- [ ] First successful build completed

### Verify Security

```bash
# Check last commit signature
git log -1 --show-signature

# Should show:
# gpg: Signature made [date]
# gpg:                using RSA key [YOUR_KEY_ID]
# gpg: Good signature from "Your Name <email>"
```

---

## 📊 Monitor System

### Check Build Status

Visit: `https://github.com/kingtravismoc/ktmoc-nexus/actions`

Look for:
- ✅ Green checkmarks on all jobs
- "Verified: Sole launcher kingtravismoc"
- APK artifact available

### View Logs

In web console:
- Auto-refreshes every 5 seconds
- Shows all deployments
- Tracks method approvals/blocks

### Check Version

```bash
# On Android device
adb shell dumpsys package com.ktmoc.nexus | grep versionName

# Or in app: Settings → About
```

---

## ⚡ Force Update (Emergency)

If you need to push critical security patch:

### Via Web Console

1. Go to "REPOSITORY STATUS"
2. Click "⚡ Force Update"
3. Confirm action
4. Wait for deployment (~2 minutes)

### Via GitHub

1. Go to Actions tab
2. Select "🔐 Sole Launcher Verified Build & Deploy"
3. Click "Run workflow"
4. Check "Force update to I2PD/F-Droid"
5. Click "Run workflow"

---

## 🆘 Troubleshooting

### Build Fails with "Unauthorized"

**Problem**: Commit not signed  
**Fix**: `git commit -S -m "message"` and push again

### Web Console Won't Start

**Problem**: Missing dependencies  
**Fix**: `pip install -r requirements.txt`

### I2PD Connection Refused

**Problem**: i2pd not running  
**Fix**: `sudo systemctl start i2pd`

### APK Installation Blocked

**Problem**: Unknown sources disabled  
**Fix**: Android Settings → Security → Enable "Unknown sources"

---

## 📞 Support Resources

- **Documentation**: `/docs/README.md`
- **Security Protocol**: `/SECURITY_PROTOCOL.md`
- **F-Droid Guide**: `/FDROID_README.md`
- **GitHub Issues**: https://github.com/kingtravismoc/ktmoc-nexus/issues
- **I2P Eepsite**: (Your .b32.i2p address)

---

## ✅ Next Steps

After setup is complete:

1. **Customize Branding**: Update watermark in all files
2. **Configure Regions**: Set appropriate region locks
3. **Add Services**: Use AI to generate shims for your APIs
4. **Deploy to F-Droid**: Submit repository URL
5. **Monitor Usage**: Check web console regularly

---

*Quick Start Guide v1.0*  
*Sole Launcher: @kingtravismoc*  
*Watermarked for KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU*
