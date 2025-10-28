# ===================================================
# Tic-Tac-Toe Project - Complete Launcher (PowerShell)
# ===================================================
# This script launches all components:
# 1. Java Server (WebSocket + HTTP REST)
# 2. Documentation (Live Server)
# 3. Browser Control Center
# ===================================================

Write-Host ""
Write-Host "=====================================================" -ForegroundColor Magenta
Write-Host "   🎮 Tic-Tac-Toe Project - Complete Launcher" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Magenta
Write-Host ""

# Check if running from correct directory
if (-not (Test-Path "java-server")) {
    Write-Host "ERROR: java-server folder not found!" -ForegroundColor Red
    Write-Host "Please run this script from the root directory of the project." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Check Java installation
Write-Host "[1/5] Checking Java installation..." -ForegroundColor Yellow
try {
    java -version 2>&1 | Out-Null
    Write-Host "[+] Java found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Java is not installed or not in PATH!" -ForegroundColor Red
    Write-Host "Please install Java 11+ and add it to your PATH." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Build server
Write-Host ""
Write-Host "[2/5] Building Java Server..." -ForegroundColor Yellow
Push-Location "java-server"

# Check if gradlew exists
if (-not (Test-Path "gradlew.bat")) {
    Write-Host "ERROR: gradlew.bat not found!" -ForegroundColor Red
    Pop-Location
    Read-Host "Press Enter to exit"
    exit 1
}

# Run gradle build
& .\gradlew.bat build 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    Pop-Location
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "[+] Server built successfully" -ForegroundColor Green
Pop-Location

# Start server
Write-Host ""
Write-Host "[3/5] Starting Java Server..." -ForegroundColor Yellow

# Kill any existing processes on ports 8080 and 8081
$processes = Get-NetTCPConnection -LocalPort 8080, 8081 -ErrorAction SilentlyContinue
if ($processes) {
    Write-Host "Cleaning up existing processes..." -ForegroundColor Yellow
    foreach ($process in $processes) {
        Stop-Process -Id $process.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

# Start new server in new window
$currentDir = Get-Location
$serverDir = Join-Path $currentDir "java-server"
Start-Process cmd -ArgumentList "/k", "cd `"$serverDir`" && java -jar build/libs/ttt-server-1.0.0.jar" -WindowStyle Normal
Write-Host "[+] Server starting (window will open)" -ForegroundColor Green
Start-Sleep -Seconds 3

# Check if server is running
Write-Host ""
Write-Host "[4/5] Verifying server connection..." -ForegroundColor Yellow
$maxAttempts = 10
$attempt = 0
$serverRunning = $false

while ($attempt -lt $maxAttempts) {
    try {
        Invoke-WebRequest -Uri "http://localhost:8081/health" -TimeoutSec 2 -ErrorAction Stop | Out-Null
        $serverRunning = $true
        Write-Host "[+] Server is running and responsive" -ForegroundColor Green
        break
    } catch {
        $attempt++
        if ($attempt -lt $maxAttempts) {
            Write-Host "  Waiting for server to start... ($attempt/$maxAttempts)" -ForegroundColor Yellow
            Start-Sleep -Seconds 1
        }
    }
}

if (-not $serverRunning) {
    Write-Host "[!] Server may not have started. Check the server window for errors." -ForegroundColor Yellow
}

# Open Control Center
Write-Host ""
Write-Host "[5/5] Opening Control Center..." -ForegroundColor Yellow
$launchCenterPath = Join-Path (Get-Location) "LAUNCH_CENTER.html"
if (Test-Path $launchCenterPath) {
    & $launchCenterPath
    Write-Host "[+] Control Center opened" -ForegroundColor Green
} else {
    Write-Host "[!] LAUNCH_CENTER.html not found" -ForegroundColor Yellow
}

# Display summary
Write-Host ""
Write-Host "=====================================================" -ForegroundColor Green
Write-Host "   [OK] All components launched!" -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Green
Write-Host ""
Write-Host "SERVER STATUS:" -ForegroundColor Cyan
Write-Host "   * WebSocket:  ws://localhost:8080" -ForegroundColor White
Write-Host "   * HTTP REST:  http://localhost:8081" -ForegroundColor White
Write-Host "   * Health:     http://localhost:8081/health" -ForegroundColor White
Write-Host ""
Write-Host "DOCUMENTATION:" -ForegroundColor Cyan
Write-Host "   * Control Center: LAUNCH_CENTER.html" -ForegroundColor White
Write-Host "   * Architecture:   index.html (5 parts)" -ForegroundColor White
Write-Host ""
Write-Host "GAME CLIENT:" -ForegroundColor Cyan
Write-Host "   Open defold-client/ folder in Defold and press F5" -ForegroundColor White
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Cyan
Write-Host "   1. Check the server window for startup messages" -ForegroundColor White
Write-Host "   2. Use the Control Center to manage components" -ForegroundColor White
Write-Host "   3. View documentation or launch the game" -ForegroundColor White
Write-Host ""
Write-Host "=====================================================" -ForegroundColor Green
Write-Host ""

Write-Host "Press Enter to close this window..." -ForegroundColor Yellow
Read-Host
