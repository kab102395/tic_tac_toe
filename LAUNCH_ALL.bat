@echo off
REM ===================================================
REM Tic-Tac-Toe Project - Complete Launcher
REM ===================================================
REM This script launches all components:
REM 1. Java Server (WebSocket + HTTP REST)
REM 2. Documentation (Live Server)
REM 3. Browser Dashboard
REM ===================================================

setlocal enabledelayedexpansion

echo.
echo ====================================================
echo   🎮 Tic-Tac-Toe Project - Complete Launcher
echo ====================================================
echo.

REM Check if running from correct directory
if not exist "java-server" (
    echo ERROR: java-server folder not found!
    echo Please run this script from the root directory of the project.
    pause
    exit /b 1
)

echo [1/4] Checking Java installation...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH!
    echo Please install Java 11+ and add it to your PATH.
    pause
    exit /b 1
)
echo ✓ Java found

echo.
echo [2/4] Building Java Server...
cd java-server
call gradlew build >nul 2>&1
if errorlevel 1 (
    echo ERROR: Build failed! Check java-server for errors.
    pause
    exit /b 1
)
echo ✓ Server built successfully
cd ..

echo.
echo [3/4] Starting Java Server...
start "TTT-Server" cmd /k "cd java-server && java -jar build/libs/ttt-server-1.0.0.jar"
timeout /t 3 /nobreak
echo ✓ Server starting (window will open)

echo.
echo [4/4] Opening Control Center...
timeout /t 1 /nobreak

REM Try to open with default browser
start LAUNCH_CENTER.html

echo.
echo ====================================================
echo   ✅ All components launched!
echo ====================================================
echo.
echo 📊 Server Status:
echo   • WebSocket:  ws://localhost:8080
echo   • HTTP REST:  http://localhost:8081
echo   • Health:     http://localhost:8081/health
echo.
echo 📚 Documentation:
echo   • Control Center: LAUNCH_CENTER.html (opened in browser)
echo   • Architecture:   index.html (all documentation parts)
echo.
echo 🎮 Game Client:
echo   Open defold-client/ folder in Defold and press F5
echo.
echo 📋 Next Steps:
echo   1. Wait for server window to show "Server ready..."
echo   2. Open the Control Center in your browser
echo   3. View documentation or launch the game
echo.
echo ====================================================
echo.
pause
