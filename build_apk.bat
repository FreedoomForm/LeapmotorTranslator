@echo off
REM Build script for LeapmotorTranslator APK
REM Run this script from the LeapmotorTranslator directory

echo ========================================
echo Leapmotor Translator APK Build Script
echo ========================================
echo.

REM Check for Java
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Please install JDK 17+
    echo Download from: https://adoptium.net/
    exit /b 1
)

REM Check for ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    echo WARNING: ANDROID_HOME not set
    echo Trying common locations...
    
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
        echo Found Android SDK at: %ANDROID_HOME%
    ) else (
        echo ERROR: Android SDK not found
        echo Please install Android Studio or set ANDROID_HOME
        exit /b 1
    )
)

echo.
echo Building Debug APK...
echo.

REM Clean previous build
call gradlew.bat clean

REM Build debug APK
call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESS
echo ========================================
echo.
echo APK location:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
echo To install on device:
echo   adb install app\build\outputs\apk\debug\app-debug.apk
echo.

pause
