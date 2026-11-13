[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path $PSScriptRoot '..\cujammu-pulse-android'),
    [string]$SocketTempDirectory = '',
    [string[]]$GradleArguments = @('testDebugUnitTest', 'assembleDebug', '--no-daemon', '--console=plain')
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath($ProjectPath)
$wrapper = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper)) { throw "Gradle wrapper not found: $wrapper" }

if (-not $SocketTempDirectory) {
    $shortWritableRoot = if (Test-Path -LiteralPath 'C:\tools' -PathType Container) { 'C:\tools' } else { $env:TEMP }
    $SocketTempDirectory = Join-Path $shortWritableRoot 'cujammu-gradle-sockets'
}
$SocketTempDirectory = [IO.Path]::GetFullPath($SocketTempDirectory)
if ($SocketTempDirectory.Length -gt 80 -or $SocketTempDirectory.Contains(' ')) {
    throw 'The JDK Windows loopback-pipe workaround needs a short socket temp path (80 characters or fewer, no spaces). Pass -SocketTempDirectory, for example C:\tools\cujammu-sockets.'
}
New-Item -ItemType Directory -Path $SocketTempDirectory -Force | Out-Null

$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$previousLocation = Get-Location
$exitCode = 1
try {
    $unixSocketOption = '-Djdk.net.unixdomain.tmpdir="' + $SocketTempDirectory + '"'
    $env:JAVA_TOOL_OPTIONS = if ($previousJavaToolOptions) {
        $previousJavaToolOptions + ' ' + $unixSocketOption
    } else {
        $unixSocketOption
    }
    Set-Location -LiteralPath $projectRoot
    & $wrapper @GradleArguments
    $exitCode = $LASTEXITCODE
} finally {
    Set-Location -LiteralPath $previousLocation
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
}
exit $exitCode
