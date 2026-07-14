# 🔒 SOLE LAUNCHER SECURITY PROTOCOL

## Overview
This system ensures that **ONLY** the designated "Sole Launcher" (Owner) can push updates to the KTMOC NEXUS repository, build the application, and deploy to F-Droid/I2PD. This prevents unauthorized modifications, supply chain attacks, and ensures the integrity of hot-patches for dangerous use cases.

## Architecture

### 1. GPG Commit Signing (Primary Gate)
Every commit pushed to `main` or `release` branches **MUST** be signed with the Sole Launcher's private GPG key.
- **Mechanism**: `git commit -S`
- **Verification**: GitHub Actions workflow `sole-launcher-verify.yml` runs `git verify-commit`.
- **Failure Mode**: If the signature is missing or does not match the stored public key fingerprint, the CI/CD pipeline fails immediately, preventing builds and deployments.

### 2. GitHub Secrets Configuration
The following secrets must be configured in your GitHub Repository Settings (`Settings` > `Secrets and variables` > `Actions`):

| Secret Name | Description | Example Value |
| :--- | :--- | :--- |
| `OWNER_GPG_PUBLIC_KEY` | The ASCII-armored public key of the Sole Launcher. | `-----BEGIN PGP PUBLIC KEY BLOCK-----...` |
| `OWNER_GPG_KEY_ID` | The fingerprint/Key ID of the owner's GPG key. | `A1B2C3D4E5F6G7H8` |
| `SOLE_LAUNCHER_USERNAME` | The GitHub username of the Sole Launcher. | `ktmoc-owner` |
| `ANDROID_RELEASE_KEYSTORE` | Base64 encoded Android Release Keystore. | `MIIJ... (long string)` |
| `KEYSTORE_PASSWORD` | Password for the Android Keystore. | `StrongPassword123!` |
| `KEY_ALIAS` | Alias for the signing key inside the keystore. | `ktmoc-release` |
| `KEY_PASSWORD` | Password for the specific key alias. | `KeyPassword123!` |
| `REPO_GPG_PRIVATE_KEY` | Private key used to sign the F-Droid `index.xml`. | `-----BEGIN PGP PRIVATE KEY BLOCK-----...` |
| `REPO_GPG_KEY_ID` | Key ID for the repo signing key. | `X1Y2Z3...` |

### 3. Branch Protection Rules
Configure these in GitHub (`Settings` > `Branches` > `Add branch protection rule`):

- **Branch name pattern**: `main`, `release`
- ✅ **Require a pull request before merging**: Enabled
- ✅ **Require approvals**: 1 (Optional, but recommended)
- ✅ **Require status checks to pass before merging**: Enabled
    - Status check: `verify-signature`
- ✅ **Require branches to be up to date before merging**: Enabled
- ✅ **Include administrators**: **CRITICAL** (Ensures even the owner must follow rules if desired, though owner signs commits).
- ✅ **Restrict who can push to matching branches**: Only `SOLE_LAUNCHER_USERNAME`.

### 4. Workflow Logic (`sole-launcher-verify.yml`)

1. **Trigger**: On any `push` or `pull_request` to protected branches.
2. **Step 1: Import Key**: Imports the trusted Owner Public Key from secrets.
3. **Step 2: Verify Signature**:
    - Runs `git verify-commit HEAD`.
    - Extracts the signer's Key ID.
    - Compares against `OWNER_GPG_KEY_ID`.
    - **If mismatch**: Job fails, build stops, no deployment occurs.
4. **Step 3: Build & Sign**: Only runs if verification passes.
5. **Step 4: Deploy**: Pushes signed artifacts to I2PD/F-Droid.

## Setup Instructions for the Sole Launcher

### Step 1: Generate GPG Key (If you don't have one)
```bash
gpg --full-generate-key
# Select RSA (4096), set expiration (optional but recommended), provide your identity.
```

### Step 2: Export Public Key
```bash
gpg --armor --export YOUR_EMAIL@example.com > owner_public.key
```
*Copy the contents of `owner_public.key` into the GitHub Secret `OWNER_GPG_PUBLIC_KEY`.*

### Step 3: Get Key ID
```bash
gpg --list-keys --keyid-format LONG
# Copy the long ID (e.g., A1B2C3D4E5F6G7H8) into `OWNER_GPG_KEY_ID`.
```

### Step 4: Configure Git to Sign Commits
On your local machine:
```bash
git config --global user.signingkey YOUR_KEY_ID
git config --global commit.gpgsign true
# Optional: Sign all tags too
git config --global tag.gpgsign true
```

### Step 5: Signing Commits
Now, every time you commit:
```bash
git commit -S -m "Your message"
```
*Note: If you set `commit.gpgsign true`, standard `git commit` works automatically.*

### Step 6: Verify Locally
Before pushing, verify your signature:
```bash
git verify-commit HEAD
```

## Security Guarantees

1. **Identity**: Only commits signed by the specific GPG key are accepted.
2. **Integrity**: Any modification to the code after signing invalidates the signature.
3. **Non-Repudiation**: The cryptographic signature proves the Sole Launcher authored the update.
4. **Supply Chain Safety**: Prevents attackers from injecting malicious code via compromised CI accounts or stolen GitHub tokens, as they cannot forge the GPG signature.

## Emergency Revocation
If the Sole Launcher's private key is compromised:
1. Generate a new GPG key pair immediately.
2. Update `OWNER_GPG_PUBLIC_KEY` and `OWNER_GPG_KEY_ID` in GitHub Secrets.
3. Revoke the old key on public key servers.
4. Force update the `version.json` to require a new minimum version if necessary.

## Integration with Version Checker
The `VersionChecker.kt` in the app now also validates the **signature of the release metadata**.
- It downloads `version.json.sig` (signed by the Repo GPG Key).
- Verifies it against the hardcoded public key in the app.
- Ensures the version info itself hasn't been tampered with on the server/I2P eepsite.

This creates a **Chain of Trust**:
`Sole Launcher GPG` -> `Git Commit` -> `CI Build` -> `APK Signature` + `Repo Index Signature` -> `App Verification`.
