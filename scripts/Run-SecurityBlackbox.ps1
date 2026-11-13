param(
    [ValidateRange(1024,65535)][int]$Port = 18081,
    [ValidatePattern('^[a-z][a-z0-9-]{0,63}$')][string]$OutputName = 'security-review-2026-10-19'
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$clientRoot = Join-Path $workspaceRoot 'cujammu-pulse'
$targetRoot = Join-Path $workspaceRoot 'college-events\target'
$credentialsPath = Join-Path $targetRoot ($OutputName + '\session-credentials.json')
$reportPath = Join-Path $targetRoot ($OutputName + '-security-blackbox.json')
$origin = "http://127.0.0.1:$Port"
if (-not (Test-Path -LiteralPath $credentialsPath -PathType Leaf)) {
    throw 'The selected isolated demo credential file is missing.'
}
if (-not (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.LocalAddress -eq '127.0.0.1' })) {
    throw 'No loopback listener exists on the selected port.'
}
$rtk = (Get-Command rtk -ErrorAction Stop).Source
$previousOrigin = $env:PULSE_SECURITY_ORIGIN
$previousCredentials = $env:PULSE_SECURITY_CREDENTIALS
$previousOutput = $env:PULSE_SECURITY_OUTPUT
try {
    $env:PULSE_SECURITY_ORIGIN = $origin
    $env:PULSE_SECURITY_CREDENTIALS = $credentialsPath
    $env:PULSE_SECURITY_OUTPUT = $reportPath
    Push-Location $clientRoot
    try {
        & $rtk proxy node scripts/security-blackbox.mjs
        if ($LASTEXITCODE -ne 0) { throw "Loopback security check failed: exit $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    $env:PULSE_SECURITY_ORIGIN = $previousOrigin
    $env:PULSE_SECURITY_CREDENTIALS = $previousCredentials
    $env:PULSE_SECURITY_OUTPUT = $previousOutput
}
