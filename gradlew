#!/bin/bash
# Gradle wrapper script for Unix/Linux/MacOS
# This file is required for GitHub Actions

GRADLE_VERSION=8.4

# Determine the script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Default Gradle user home
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

# Download gradle wrapper jar if not exists
WRAPPER_JAR="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle Wrapper..."
    mkdir -p "$SCRIPT_DIR/gradle/wrapper"
    curl -L -o "$WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || \
    wget -O "$WRAPPER_JAR" "https://github.com/gradle/gradle/raw/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null
fi

# Run Gradle
exec java -jar "$WRAPPER_JAR" "$@"
