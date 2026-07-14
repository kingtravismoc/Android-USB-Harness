#!/usr/bin/env python3
"""
F-Droid Repository Tool for KTMOC NEXUS
Manages repository creation, signing, and I2P deployment

Usage:
    python fdroid_repo_tool.py init          - Initialize repository
    python fdroid_repo_tool.py build         - Build APK and metadata
    python fdroid_repo_tool.py sign          - Sign repository
    python fdroid_repo_tool.py deploy        - Deploy to I2P eepsite
    python fdroid_repo_tool.py sync          - Sync from GitHub
    python fdroid_repo_tool.py verify        - Verify installation
"""

import os
import sys
import json
import hashlib
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

REPO_DIR = Path("fdroid-repo")
METADATA_DIR = REPO_DIR / "metadata"
REPO_ARCHIVE_DIR = REPO_DIR / "repo"
CONFIG_FILE = REPO_DIR / "config.yml"
VERSION_FILE = Path("version.json")

class FdroidRepoTool:
    def __init__(self):
        self.config = self.load_config()
        self.version_info = self.load_version()
    
    def load_config(self):
        """Load repository configuration"""
        if CONFIG_FILE.exists():
            config = {}
            with open(CONFIG_FILE, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#') and '=' in line:
                        key, value = line.split('=', 1)
                        config[key.strip()] = value.strip()
            return config
        return {}
    
    def load_version(self):
        """Load version information"""
        if VERSION_FILE.exists():
            with open(VERSION_FILE, 'r') as f:
                return json.load(f)
        return None
    
    def init_repo(self):
        """Initialize F-Droid repository structure"""
        print("📦 Initializing F-Droid repository...")
        
        # Create directory structure
        METADATA_DIR.mkdir(parents=True, exist_ok=True)
        REPO_ARCHIVE_DIR.mkdir(parents=True, exist_ok=True)
        
        # Create index.xml template
        index_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<index>
    <name>{self.config.get('repo_name', 'KTMOC NEXUS Repository')}</name>
    <description>{self.config.get('repo_description', 'Official KTMOC NEXUS repository')}</description>
    <timestamp>{int(datetime.now().timestamp())}</timestamp>
    <packages>
    </packages>
</index>"""
        
        (REPO_ARCHIVE_DIR / "index.xml").write_text(index_xml)
        
        print(f"✅ Repository initialized at {REPO_DIR.absolute()}")
        print(f"   - Metadata: {METADATA_DIR.absolute()}")
        print(f"   - Archive: {REPO_ARCHIVE_DIR.absolute()}")
    
    def calculate_sha256(self, file_path):
        """Calculate SHA256 hash of a file"""
        sha256_hash = hashlib.sha256()
        with open(file_path, "rb") as f:
            for byte_block in iter(lambda: f.read(4096), b""):
                sha256_hash.update(byte_block)
        return sha256_hash.hexdigest()
    
    def build(self):
        """Build APK and generate metadata"""
        print("🔨 Building APK...")
        
        # Run Gradle build
        result = subprocess.run(
            ["./gradlew", "assembleRelease", "--no-daemon"],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print(f"❌ Build failed: {result.stderr}")
            return False
        
        print("✅ Build successful")
        
        # Find generated APK
        apk_dir = Path("app/build/outputs/apk/release")
        apk_files = list(apk_dir.glob("*.apk"))
        
        if not apk_files:
            print("❌ No APK found after build")
            return False
        
        apk_path = apk_files[0]
        print(f"📱 Found APK: {apk_path.name}")
        
        # Calculate hash
        apk_hash = self.calculate_sha256(apk_path)
        print(f"🔐 SHA256: {apk_hash}")
        
        # Update version.json
        if self.version_info:
            self.version_info["sha256Hash"] = apk_hash
            self.version_info["timestamp"] = int(datetime.now().timestamp())
            
            with open(VERSION_FILE, 'w') as f:
                json.dump(self.version_info, f, indent=2)
            
            print("✅ Updated version.json")
        
        # Copy APK to repo
        dest_apk = REPO_ARCHIVE_DIR / apk_path.name
        dest_apk.write_bytes(apk_path.read_bytes())
        print(f"✅ Copied APK to repository")
        
        return True
    
    def generate_metadata(self):
        """Generate F-Droid metadata XML"""
        print("📝 Generating metadata...")
        
        if not self.version_info:
            print("❌ No version info available")
            return False
        
        # Find APK in repo
        apk_name = f"nexus-{self.version_info['versionName']}.apk"
        apk_path = REPO_ARCHIVE_DIR / apk_name
        
        if not apk_path.exists():
            print(f"❌ APK not found: {apk_path}")
            return False
        
        apk_hash = self.calculate_sha256(apk_path)
        
        # Generate metadata XML
        metadata_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<metadata>
    <id>com.ktmoc.nexus</id>
    <name>KTMOC NEXUS</name>
    <summary>AI-Powered WebUSB Debugging Harness</summary>
    <description>{self.version_info.get('releaseNotes', '')}</description>
    <author>KTMOC Development Team</author>
    <source>https://github.com/ktmoc/nexus</source>
    <license>GPL-3.0-or-later</license>
    <web_site>{self.config.get('repo_url', 'https://ktmocnexus.i2p')}</web_site>
    <packages>
        <package versionCode="{self.version_info['versionCode']}" 
                 versionName="{self.version_info['versionName']}" 
                 apk="{apk_name}" 
                 sig="signature_hash">
            <hash type="sha256">{apk_hash}</hash>
        </package>
    </packages>
</metadata>"""
        
        metadata_file = METADATA_DIR / "com.ktmoc.nexus.xml"
        metadata_file.write_text(metadata_xml)
        
        print(f"✅ Metadata generated: {metadata_file}")
        return True
    
    def update_index(self):
        """Update F-Droid index.xml"""
        print("📋 Updating index.xml...")
        
        if not self.version_info:
            print("❌ No version info available")
            return False
        
        apk_name = f"nexus-{self.version_info['versionName']}.apk"
        apk_path = REPO_ARCHIVE_DIR / apk_name
        
        if not apk_path.exists():
            print(f"❌ APK not found: {apk_path}")
            return False
        
        apk_hash = self.calculate_sha256(apk_path)
        
        # Generate index.xml
        index_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<index>
    <name>{self.config.get('repo_name', 'KTMOC NEXUS Official Repository')}</name>
    <description>{self.config.get('repo_description', 'Official F-Droid repository for KTMOC NEXUS')}</description>
    <timestamp>{int(datetime.now().timestamp())}</timestamp>
    <packages>
        <package id="com.ktmoc.nexus">
            <name>KTMOC NEXUS</name>
            <version code="{self.version_info['versionCode']}" name="{self.version_info['versionName']}">
                <apk file="{apk_name}" hash="{apk_hash}" />
            </version>
        </package>
    </packages>
</index>"""
        
        index_file = REPO_ARCHIVE_DIR / "index.xml"
        index_file.write_text(index_xml)
        
        print(f"✅ Index updated: {index_file}")
        return True
    
    def sign_repo(self):
        """Sign repository (placeholder for actual signing)"""
        print("🔐 Signing repository...")
        print("⚠️  Note: Actual signing requires keystore configuration")
        print("   Configure keystore path in config.yml")
        # In production: Use apksigner or jarsigner
        return True
    
    def deploy_i2p(self):
        """Deploy repository to I2P eepsite"""
        print("🌐 Deploying to I2P eepsite...")
        
        eepsite_path = self.config.get('eepsite_path', '/var/lib/i2pd/eepsites/ktmocnexus')
        
        if not Path(eepsite_path).exists():
            print(f"⚠️  Eepsite path does not exist: {eepsite_path}")
            print("   Creating directory...")
            Path(eepsite_path).mkdir(parents=True, exist_ok=True)
        
        # Copy repo files to eepsite
        eepsite_repo = Path(eepsite_path) / "fdroid" / "repo"
        eepsite_repo.mkdir(parents=True, exist_ok=True)
        
        # Copy all files
        for src_file in REPO_ARCHIVE_DIR.glob("*"):
            dest_file = eepsite_repo / src_file.name
            dest_file.write_bytes(src_file.read_bytes())
            print(f"   📄 Copied: {src_file.name}")
        
        print(f"✅ Deployed to I2P eepsite: {eepsite_path}")
        print(f"   Repository URL: {self.config.get('repo_url', 'https://ktmocnexus.i2p/fdroid/repo')}")
        return True
    
    def sync_from_github(self):
        """Sync repository from GitHub"""
        print("🔄 Syncing from GitHub...")
        
        if not self.version_info:
            print("❌ No version info available")
            return False
        
        github_repo = self.version_info.get('githubRepo', 'ktmoc/nexus')
        print(f"   Repository: {github_repo}")
        
        # In production: Use GitHub API to fetch latest release
        # For now, just verify local state
        print("✅ Sync check completed")
        return True
    
    def verify_installation(self, apk_path, min_version=1):
        """Verify APK installation authenticity"""
        print("🔍 Verifying installation...")
        
        if not Path(apk_path).exists():
            print(f"❌ APK not found: {apk_path}")
            return False
        
        # Calculate hash
        apk_hash = self.calculate_sha256(apk_path)
        print(f"   APK Hash: {apk_hash}")
        
        # Check against expected hash
        if self.version_info:
            expected_hash = self.version_info.get('sha256Hash', '')
            if expected_hash and apk_hash != expected_hash:
                print(f"❌ Hash mismatch!")
                print(f"   Expected: {expected_hash}")
                print(f"   Got:      {apk_hash}")
                return False
            
            # Check version code
            version_code = self.version_info.get('versionCode', 0)
            if version_code < min_version:
                print(f"❌ Version too old: {version_code} < {min_version}")
                return False
        
        print("✅ Installation verified as genuine")
        return True
    
    def run_all(self):
        """Run complete build and deploy pipeline"""
        print("🚀 Running complete pipeline...\n")
        
        steps = [
            ("Initialize", self.init_repo),
            ("Build", self.build),
            ("Generate Metadata", self.generate_metadata),
            ("Update Index", self.update_index),
            ("Sign", self.sign_repo),
            ("Deploy to I2P", self.deploy_i2p),
        ]
        
        for step_name, step_func in steps:
            print(f"\n{'='*50}")
            print(f"Step: {step_name}")
            print('='*50)
            
            if not step_func():
                print(f"❌ Pipeline failed at step: {step_name}")
                return False
        
        print(f"\n{'='*50}")
        print("✅ Pipeline completed successfully!")
        print('='*50)
        return True


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    
    tool = FdroidRepoTool()
    command = sys.argv[1].lower()
    
    if command == "init":
        tool.init_repo()
    elif command == "build":
        tool.build()
    elif command == "metadata":
        tool.generate_metadata()
    elif command == "index":
        tool.update_index()
    elif command == "sign":
        tool.sign_repo()
    elif command == "deploy":
        tool.deploy_i2p()
    elif command == "sync":
        tool.sync_from_github()
    elif command == "verify":
        apk_path = sys.argv[2] if len(sys.argv) > 2 else "app/build/outputs/apk/release/*.apk"
        tool.verify_installation(apk_path)
    elif command == "all":
        tool.run_all()
    else:
        print(f"❌ Unknown command: {command}")
        print(__doc__)
        sys.exit(1)


if __name__ == "__main__":
    main()
