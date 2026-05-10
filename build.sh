#!/bin/bash
set -e

echo "[ ANDROID BUILD SCRIPT ]"
echo "========================"
echo ""

# Detect Android SDK
if [ -z "$ANDROID_HOME" ]; then
    for dir in \
        "$HOME/Library/Android/sdk" \
        "$HOME/Android/Sdk" \
        "/opt/homebrew/share/android-commandlinetools" \
        "/usr/local/share/android-commandlinetools"; do
        if [ -d "$dir" ]; then
            export ANDROID_HOME="$dir"
            break
        fi
    done
    if [ -z "$ANDROID_HOME" ]; then
        echo "ERROR: ANDROID_HOME not set and SDK not found."
        exit 1
    fi
    echo "> ANDROID_HOME: $ANDROID_HOME"
fi

# Create local.properties
if [ ! -f "local.properties" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    echo "> Created local.properties"
fi

# Ensure gradle wrapper jar exists
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "> Downloading gradle-wrapper.jar..."
    mkdir -p gradle/wrapper
    curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar" \
        -o "$WRAPPER_JAR" || wget -q \
        "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar" \
        -O "$WRAPPER_JAR"
fi

if [ ! -f "gradlew" ]; then
    echo "ERROR: gradlew not found."
    exit 1
fi
chmod +x gradlew

# Generate signing keystore if missing
KEYSTORE="release-key.jks"
if [ ! -f "$KEYSTORE" ]; then
    echo "> Generating signing key..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -alias release \
        -storepass android123 -keypass android123 \
        -dname "CN=App, OU=Dev, O=Dev, L=Unknown, ST=Unknown, C=US"
fi

echo "> Cleaning..."
./gradlew clean

echo "> Building signed release APK..."
./gradlew assembleRelease

SIGNED_APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$SIGNED_APK" ]; then
    SIZE=$(du -h "$SIGNED_APK" | cut -f1)
    echo ""
    echo "========================"
    echo "> BUILD SUCCESSFUL"
    echo "> APK: $SIGNED_APK"
    echo "> Size: $SIZE"
    echo "========================"
    echo ""
    echo "Install: adb install -r $SIGNED_APK"
else
    echo "ERROR: APK not found."
    exit 1
fi
