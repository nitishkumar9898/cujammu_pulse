# CUJAMMU-PULSE Locust Web UI Runner
# Runs Locust with web UI for interactive load testing.
#
# Usage:
#   .\run-locust-web.ps1 -Users 50 -RunTime 10m
#
# Opens http://localhost:8089 for UI, targets http://localhost:18080

param(
    [int]$Users = 50,
    [int]$SpawnRate = 10,
    [string]$RunTime = "10m",
    [string]$Host = "http://localhost:18080"
)

$ErrorActionPreference = "Stop"

# Check Python/locust
try {
    $version = python -c "import locust; print(locust.__version__)"
    Write-Host "Locust $version loaded" -ForegroundColor Green
} catch {
    Write-Host "Install locust: pip install locust==2.32.0" -ForegroundColor Red
    exit 1
}

# Check backend health
try {
    $health = Invoke-WebRequest -Uri "$Host/api/ready" -UseBasicParsing -TimeoutSec 5
    Write-Host "Backend OK: $($health.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Backend unreachable at $Host" -ForegroundColor Red
    Write-Host "Start: java -jar target/events-1.0.0.jar -Dspring.profiles.active=professor-demo"
    exit 1
}

# Run locust with web UI
Write-Host "Starting Locust UI at http://localhost:8089" -ForegroundColor Cyan
Write-Host "Target: $Host" -ForegroundColor Cyan
Write-Host "Users: $Users, Spawn rate: $SpawnRate/sec, Duration: $RunTime" -ForegroundColor Cyan
Write-Host ""

python -m locust --host="$Host" -u $Users -r $SpawnRate -t $RunTime