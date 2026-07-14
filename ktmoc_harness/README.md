# KTMOC Harness - F-Droid Compatible WebUSB Android Application

## Overview

KTMOC CRT v4.0+ is an F-Droid compatible Android application that serves as a WebUSB computer harness for debugging and interrogation of laptops. It features:

- **WebUSB Support**: Native USB device access through Android's USB Host API
- **AI-Powered Analysis**: Integration with Hugging Face and other AI services for UI analysis
- **i2pd Eepsite Compatible**: Can access .i2p domains and work with I2P network
- **K-Bank Storage**: Persistent storage for scan data and element registry
- **CRT Terminal Interface**: Retro terminal-style UI for debugging sessions

## Features

### Core Functionality
- Connect to USB devices for hardware debugging
- Capture screen/display content for analysis
- AI-powered UI element detection and analysis
- Synthesis of multiple scans for improved accuracy
- Gap checking against registry
- Diagnostic reporting

### AI Integration
- **Hugging Face No-Key Mode**: Free inference without API key
- **Custom AI Services**: Configure any AI service with API key
- **Automatic Documentation Fetching**: Pull API docs from service URLs
- **Shim Generation**: Auto-generate integration code

### F-Droid Compatibility
- All free and open source software dependencies
- No proprietary blobs or non-free components
- Proper permission declarations
- Reproducible build support

## Project Structure

```
ktmoc_harness/
├── app/
│   ├── src/main/
│   │   ├── java/com/ktmoc/harness/
│   │   │   ├── KtmocHarnessActivity.java    # Main activity with WebView
│   │   │   ├── KtmocApplication.java        # Application class
│   │   │   ├── UsbPermissionReceiver.java   # USB event handler
│   │   │   ├── AiProcessingService.java     # Background AI service
│   │   │   └── AiConfigDialog.java          # AI configuration dialog
│   │   ├── assets/
│   │   │   └── ktmoc.html                   # CRT interface (from original)
│   │   ├── res/
│   │   │   ├── values/strings.xml           # String resources
│   │   │   ├── values/themes.xml            # App themes
│   │   │   └── xml/                         # Config files
│   │   └── AndroidManifest.xml              # App manifest
│   └── build.gradle                         # Build configuration
├── build.gradle                             # Root build file
└── settings.gradle                          # Project settings
```

## Building

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 8 or higher
- Android SDK 21+ (minSdk), 34 (targetSdk)

### Build Commands

```bash
cd ktmoc_harness
./gradlew assembleDebug
```

For release build:
```bash
./gradlew assembleRelease
```

## Usage

### Connecting to USB Devices
1. Launch the app
2. Tap "CONNECT (WebUSB)"
3. Select USB device from the list
4. Grant USB permission when prompted

### AI Configuration
1. Tap "AI CONFIG"
2. Enter service name (e.g., "huggingface")
3. Enter model name (e.g., "microsoft/Phi-3-mini-4k-instruct")
4. Optionally enter API key
5. Enable "No-Key Mode" for free HuggingFace inference

### Keyboard Shortcuts
- `CTRL+O`: Connect/Disconnect
- `CTRL+C`: Capture screen
- `CTRL+A`: Analyze with AI
- `CTRL+H`: Run diagnostics
- `ESC`: Clear

## i2pd Integration

The app supports i2pd eepsites by:
- Allowing cleartext traffic to .i2p domains
- Configuring network security for I2P
- Supporting HTTP/HTTPS URL schemes

Configure your i2pd proxy in Android system settings, then access eepsites directly.

## K-Bank Data Export

Export scan data and registry:
```javascript
exportData()  // Downloads JSON file
```

Or use the menu option in the app.

## License

SPDX-License-Identifier: GPL-3.0-or-later

Copyright 2024 KTMOC Project

## Metadata for F-Droid

```yaml
Categories:
  - Development
  - System
License: GPL-3.0-or-later
Web Site: https://github.com/ktmoc/harness
Source Code: https://github.com/ktmoc/harness
Issue Tracker: https://github.com/ktmoc/harness/issues

Provides:
  - com.ktmoc.harness

Anti Features:
  - NonFreeNet (optional, if using proprietary AI services)

Requires Root: No

Description:
  KTMOC CRT v4.0+ is a WebUSB debugging harness for Android.
  Features AI-powered UI analysis, USB device access, and i2pd compatibility.
  
  Use it to debug laptop connections, analyze UI elements with AI,
  and store scan data in K-Bank for later synthesis.

```

## Contributing

Contributions welcome! Please ensure all dependencies are F-Droid compatible.
