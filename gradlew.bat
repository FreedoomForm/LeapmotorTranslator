@rem Gradle startup script for Windows

@if "%DEBUG%"=="" @echo off

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

set JAVA_EXE=java.exe

if defined JAVA_HOME goto findJavaFromJavaHome

echo JAVA_HOME is not set. Trying to find Java...
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: Java not found in PATH and JAVA_HOME is not defined.
echo.
echo Please install JDK 17:
echo   https://adoptium.net/temurin/releases/?version=17
echo.
goto fail

:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
goto fail

:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
exit /b %ERRORLEVEL%

:fail
exit /b 1
