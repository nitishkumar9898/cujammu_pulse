# CUJAMMU-PULSE Load Test Runner
# Runs Locust load tests against the running backend.
#
# Usage:
#   .\run-load-test.ps1 -Users 50 -RunTime 60s
#   .\run-load-test.ps1 -Users 100 -RunTime 120s -Headless
#
# Requires: Python 3.9+ with locust installed (pip install locust==2.32.0)
# Also requires: Backend running on http://localhost:18080

param(
    [int]$Users = 50,
    [int]$SpawnRate = 10,
    [string]$RunTime = "60s",
    [switch]$Headless,
    [string]$Host = "http://localhost:18080",
    [string]$OutputDir = "load-test/results"
)

$ErrorActionPreference = "Stop"

# Check Python and locust
try {
    $locustVersion = python -c "import locust; print(locust.__version__)" 2>$null
    if (-not $locustVersion) { throw "locust not found" }
    Write-Host "Locust version: $locustVersion" -ForegroundColor Green
} catch {
    Write-Host "Locust not installed. Run: pip install locust==2.32.0" -ForegroundColor Red
    exit 1
}

# Check backend is running
try {
    $health = Invoke-WebRequest -Uri "$Host/api/ready" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    Write-Host "Backend health check: $($health.StatusCode) OK" -ForegroundColor Green
} catch {
    Write-Host "Backend not reachable at $Host" -ForegroundColor Red
    Write-Host "Start backend first: java -jar target/events-1.0.0.jar"
    exit 1
}

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

# Build locust command
$locustArgs = @(
    "-f", "load-test/locustfile.py",
    "--host=$Host",
    "-u", $Users,
    "-r", $SpawnRate,
    "-t", $RunTime,
    "--only-summary",
    "--html=$OutputDir/load-test-report.html",
    "--csv=$OutputDir/load-test-stats"
)

if ($Headless) {
    $locustArgs += "--headless"
} else {
    Write-Host ""
    Write-Host "Starting Locust web UI at http://localhost:8089" -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to stop" -ForegroundColor Cyan
    Write-Host ""
}

Write-Host "Load test configuration:" -ForegroundColor Cyan
Write-Host "  Users: $Users" -ForegroundColor White
Write-Host "  Spawn rate: $SpawnRate/sec" -ForegroundColor White
Write-Host "  Run time: $RunTime" -ForegroundColor White
Write-Host "  Target: $Host" -ForegroundColor White
Write-Host ""

# Run locust
if ($Headless) {
    python -m locust $locustArgs
} else {
    python -m locust $locustArgs
}

# Show summary
if (Test-Path "$OutputDir/load-test-stats.stats.csv") {
    Write-Host ""
    Write-Host "=== LOAD TEST RESULTS ===" -ForegroundColor Green
    Get-Content "$OutputDir/load-test-stats.stats.csv" | ForEach-Object {
        Write-Host "  $_" -ForegroundColor White
    }
    Write-Host ""
    Write-Host "HTML report: $OutputDir/load-test-report.html" -ForegroundColor Yellow
}