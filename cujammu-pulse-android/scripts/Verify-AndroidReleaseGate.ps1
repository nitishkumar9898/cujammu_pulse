$ErrorActionPreference = 'Stop'

$androidRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $androidRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper is missing: $gradleWrapper"
}

# This workaround is process-scoped and mirrors the repository's Windows JVM socket fix.
$socketRoot = 'C:\tools\pulse-jvm-sockets'
New-Item -ItemType Directory -Path $socketRoot -Force | Out-Null
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
try {
    if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) {
        $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$socketRoot"
    }

    $rtk = Get-Command rtk -ErrorAction Stop
    $releaseGateCases = @(
        @{ Task = ':app:assembleRelease'; ExcludedTask = 'validateProductionApiEndpoint' },
        @{ Task = ':app:signReleaseBundle'; ExcludedTask = ':app:packageReleaseBundle' },
        @{ Task = ':app:makeApkFromBundleForRelease'; ExcludedTask = ':app:packageReleaseBundle' },
        @{ Task = ':app:zipApksForRelease'; ExcludedTask = ':app:packageReleaseBundle' },
        @{ Task = ':app:extractApksForRelease'; ExcludedTask = ':app:packageReleaseUniversalApk' },
        @{ Task = ':app:extractApksFromBundleForRelease'; ExcludedTask = ':app:packageReleaseBundle' }
    )

    foreach ($case in $releaseGateCases) {
        $stdoutFile = [System.IO.Path]::GetTempFileName()
        $stderrFile = [System.IO.Path]::GetTempFileName()
        try {
            $gradleArgs = @(
                $case.Task,
                '-x', $case.ExcludedTask,
                '-PcujammuApiBaseUrl=',
                '--no-daemon'
            )
            $quotedWrapper = '"{0}"' -f $gradleWrapper
            $process = Start-Process -FilePath $rtk.Source `
                -ArgumentList (@('proxy', $quotedWrapper) + $gradleArgs) `
                -NoNewWindow -Wait -PassThru `
                -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile
            $exitCode = $process.ExitCode
            $output = @(Get-Content -LiteralPath $stdoutFile) + @(Get-Content -LiteralPath $stderrFile)
            $combinedOutput = $output -join "`n"

            if ($exitCode -eq 0 -or $combinedOutput -notmatch 'Release builds require -PcujammuApiBaseUrl') {
                $output | ForEach-Object { Write-Output $_ }
                throw "Release gate was not proven for '$($case.Task)' when excluding '$($case.ExcludedTask)' (exit $exitCode)."
            }

            Write-Output "PASS: $($case.Task) rejects an unset API endpoint when '$($case.ExcludedTask)' is excluded."
        }
        finally {
            Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
        }
    }
}
finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
}
