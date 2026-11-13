param([string]$Tests = '', [switch]$Package, [switch]$DependencyInventory,
    [string]$SocketTempDirectory = '', [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jdkRoot = @($env:PULSE_JDK21_HOME, $env:JAVA_HOME, 'C:\Program Files\Android\Android Studio1\jbr', 'C:\Program Files\Android\Android Studio\jbr') |
    Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'release')) } |
    Where-Object { (Get-Content -LiteralPath (Join-Path $_ 'release')) -match '^JAVA_VERSION="21\.' } | Select-Object -First 1
if (-not $jdkRoot) { throw 'A full Java 21 JDK is required. Set PULSE_JDK21_HOME.' }
$maven = 'C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) { throw 'Maven 3.9.9 is unavailable.' }
$previousJava = $env:JAVA_HOME
$verificationStartedAt = [DateTime]::UtcNow
try {
    $env:JAVA_HOME = $jdkRoot
    $arguments = @('-q', '-f', (Join-Path $projectRoot 'college-events\pom.xml'))
    if ($SocketTempDirectory) {
        $socketDirectory = (Resolve-Path -LiteralPath $SocketTempDirectory -ErrorAction Stop).Path
        if (-not (Test-Path -LiteralPath $socketDirectory -PathType Container) -or $socketDirectory.Length -gt 70) {
            throw 'A short existing socket temporary directory is required.'
        }
        # Explicit local JVM environment configuration, not an application bypass.
        # Windows AF_UNIX initialization can fail under the host TEMP directory.
        # This property must exist at forked-JVM startup: setting it later through
        # Surefire system properties is too late for initialized NIO internals.
        $arguments += "-DargLine=-Djdk.net.unixdomain.tmpdir=`"$socketDirectory`""
    }
    if ($DependencyInventory) {
        $arguments += @('dependency:list', '-DincludeScope=runtime', '-DoutputFile=target/security-dependencies.txt')
        & rtk proxy $maven @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Dependency inventory failed.' }
        [xml]$inventoryPom = Get-Content -LiteralPath (Join-Path $projectRoot 'college-events\pom.xml')
        $packagerVersion = [string]$inventoryPom.project.parent.version
        # Resolve the exact additional Boot packaging tool for byte identity
        # verification; it is not added as an application dependency.
        & rtk proxy $maven -q -f (Join-Path $projectRoot 'college-events\pom.xml') dependency:get "-Dartifact=org.springframework.boot:spring-boot-jarmode-tools:$packagerVersion" -Dtransitive=false
        if ($LASTEXITCODE -ne 0) { throw 'Packaging-tool inventory could not be verified.' }
        return
    }
    if ($Tests) { $arguments += "-Dtest=$Tests" }
    if ($SkipTests -and -not $Package) { throw '-SkipTests is only valid with -Package after a separately successful test run.' }
    if ($SkipTests) { $arguments += '-DskipTests' }
    $arguments += $(if ($Package) { 'verify' } else { 'test' })
    & rtk proxy $maven @arguments
    $verificationExit = $LASTEXITCODE
    if ($SkipTests) { return }
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'college-events\target\surefire-reports') -Filter 'TEST-*.xml' |
        Where-Object { $_.LastWriteTimeUtc -ge $verificationStartedAt })
    $totals = @{ tests=0; failures=0; errors=0; skipped=0 }
    $includedSuites = 0
    $reportFiles = @()
    foreach ($report in $reports) {
        [xml]$result = Get-Content -LiteralPath $report.FullName
        if ($Tests) {
            $simpleName = ([string]$result.testsuite.name -split '\.')[-1] -replace '\$.*$', ''
            $selected = @($Tests -split ',' | Where-Object { $simpleName -like (($_ -split '#')[0]) })
            if ($selected.Count -eq 0) { continue }
        }
        $includedSuites++
        $reportFiles += $report.Name
        foreach ($key in @('tests','failures','errors','skipped')) { $totals[$key] += [int]$result.testsuite.GetAttribute($key) }
    }
    $evidenceName = if ($Tests) { 'security-verification-targeted-evidence.json' } else { 'security-verification-evidence.json' }
    if ($verificationExit -ne 0) { $evidenceName = 'security-verification-failed-evidence.json' }
    @{ startedAt=$verificationStartedAt.ToString('o'); completedAt=[DateTime]::UtcNow.ToString('o'); jdk=$jdkRoot; filter=$Tests; socketTempDirectory=$SocketTempDirectory; exitCode=$verificationExit; suites=$includedSuites; totals=$totals; reportFiles=$reportFiles } |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $projectRoot ('college-events\target\'+$evidenceName)) -Encoding UTF8
    Write-Output ($totals | ConvertTo-Json -Compress)
    if ($verificationExit -ne 0) { throw "Backend verification failed: exit $verificationExit" }
} finally { $env:JAVA_HOME = $previousJava }
