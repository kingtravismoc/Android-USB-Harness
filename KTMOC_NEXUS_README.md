# KTMOC NEXUS v4.1 - F-Droid Compatible Android Application

## Overview

KTMOC NEXUS v4.1 is a skeuomorphic Android application that transforms as tasking changes, featuring:

- **Dynamic View Factory**: Automatically detects and renders different data types (JSON, CSV, text, hex dumps, logs)
- **Hugging Face AI Integration**: Free, no-key required AI for shim generation and documentation gathering
- **Skeuomorphic Transform Engine**: UI elements transform with realistic visual feedback based on task context
- **WebUSB Support**: Debug and interrogate laptops via WebUSB
- **i2pd/I2P Compatible**: Ready for deployment to I2P eepsites
- **F-Droid Compatible**: Open-source, privacy-focused build configuration

## Features

### Skeuomorphic Transformations
The UI transforms based on current task state:
- **IDLE**: Minimal visual effects
- **ANALYZING**: Cyan glow with pulsing borders
- **CONNECTING**: Green pulse animations
- **PROCESSING**: Yellow highlighting with scale effects
- **ERROR**: Red intense glow with rapid pulsing
- **SUCCESS**: Green success indicators
- **STREAMING**: Magenta flow effects

### Dynamic View System
Automatically detects and renders:
- Plain text with CRT styling
- JSON with syntax highlighting and line numbers
- CSV as styled tables
- Hex dumps with addresses and ASCII
- Log files with level-based coloring
- Custom types via AI-generated shims

### AI Integration
- **Free Hugging Face Models**: No API key required for basic functionality
- **Shim Generation**: Create custom renderers for unknown data types
- **Documentation Gathering**: Auto-fetch service documentation
- **Integration Hooks**: Generate Kotlin code for service integration
- **Optional Gemini Support**: Can use Google's Gemini if API key provided

### Chat Interface
- Command-based interaction (`/help`, `/parse`, `/detect`, `/clear`, `/shim`)
- Direct AI queries
- Context-aware responses
- Message history with color-coded senders

## Architecture

```
app/src/main/java/com/ktmoc/nexus/
├── NexusActivity.kt              # Main activity with WebView integration
├── ai/
│   └── HuggingFaceService.kt     # AI service for shim generation
├── view/
│   └── ViewFactory.kt            # Dynamic view rendering system
└── skeuomorphic/
    └── SkeuomorphicEngine.kt     # Transformation state engine
```

## Building

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 11+
- Android SDK 21+

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on device
./gradlew installDebug
```

### F-Droid Build Configuration
Add to `build.gradle`:
```groovy
android {
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

## Usage

### Basic Commands
- `/help` - Show available commands
- `/parse` - Parse input text as JSON/CSV/text
- `/detect` - Auto-detect file type
- `/clear` - Clear all views and chat
- `/shim <type>` - Generate custom renderer for type

### Keyboard Shortcuts
- `Ctrl+Enter` - Send chat message
- `ESC` - Clear all

### AI Shim Generation
1. Enter sample data in the Text I/O box
2. Click "Gen Shim" button
3. AI generates custom HTML renderer
4. Renderer stored for future use

## I2P Deployment

### Export to Eepsite
```bash
# Build release APK
./gradlew assembleRelease

# Copy to I2P webroot
cp app/build/outputs/apk/release/app-release.apk /var/lib/i2p/i2psnark/eepsite/

# Or deploy HTML assets directly
cp app/src/main/assets/*.html /var/lib/i2p/i2psnark/eepsite/
```

### I2P Configuration
- Enable hidden mode in I2P router console
- Configure eepsite with static keys
- Upload APK or HTML files to eepsite directory
- Access via `.i2p` domain

## Permissions

Required permissions in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<!-- Optional: WebUSB -->
<uses-feature android:name="android.hardware.usb.host"/>
```

## Privacy & Security

- **No Tracking**: Zero analytics or telemetry
- **Local Storage**: All data stored locally in SharedPreferences
- **Optional AI**: Works without API keys using free Hugging Face models
- **Open Source**: Full source code available for audit
- **F-Droid Ready**: Meets F-Droid inclusion criteria

## License

Same license as parent project (see LICENSE file)

## Version History

### v4.1 (Current)
- Added skeuomorphic transform engine
- Integrated Hugging Face AI (free, no-key)
- Dynamic view factory with auto-detection
- Chat interface with command system
- WebView-based hybrid architecture

### v4.0 (Previous)
- Basic CRT terminal interface
- Gemini AI integration
- Element detection and analysis
- Local storage with K_BANK

## Contributing

1. Fork the repository
2. Create feature branch
3. Submit pull request
4. Ensure F-Droid compatibility

## Support

For issues and feature requests, use the project issue tracker.
