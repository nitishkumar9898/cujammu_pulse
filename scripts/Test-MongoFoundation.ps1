[CmdletBinding()]
param(
    [ValidateRange(20000, 60000)]
    [int] $Port = 27027
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$moduleRoot = Join-Path $repoRoot 'mongo-foundation'
$runId = [guid]::NewGuid().ToString('N').Substring(0, 16)
$databasePrefix = "pulse_foundation_$runId"

$mavenCommand = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mavenCommand -and (Test-Path -LiteralPath 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd')) {
    $mavenCommand = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
}
if (-not $mavenCommand) {
    throw 'Maven is required. Install Maven 3.9+ and make mvn available on PATH.'
}

$tcp = [System.Net.Sockets.TcpClient]::new()
try {
    $connect = $tcp.ConnectAsync('127.0.0.1', $Port)
    if (-not $connect.Wait(3000)) { throw 'Connection timed out.' }
}
catch {
    throw "No MongoDB listener found at 127.0.0.1:$Port. Start a disposable local mongod using --replSet pulseFoundation, then retry. Details: $($_.Exception.Message)"
}
finally {
    $tcp.Dispose()
}

$originalLocation = Get-Location
try {
    Set-Location $moduleRoot
    & $mavenCommand -B "-Dpulse.mongo.test.port=$Port" "-Dpulse.mongo.test.db=$databasePrefix" test
    if ($LASTEXITCODE -ne 0) { throw "Mongo foundation tests failed with exit code $LASTEXITCODE." }
}
finally {
    Set-Location $originalLocation
}
