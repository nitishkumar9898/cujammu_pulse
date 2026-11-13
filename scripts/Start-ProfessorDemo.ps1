param([ValidateRange(1024,65535)][int]$Port=18080,[switch]$SkipBuild,
    [ValidatePattern('^[a-z][a-z0-9-]{0,63}$')][string]$OutputName='professor-demo',
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._-]*\.jar$')][string]$ArtifactName='events-1.0.0.jar',
    [string]$ClientDistPath='')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$backendRoot=Join-Path $projectRoot 'college-events'
$clientRoot=Join-Path $projectRoot 'cujammu-pulse'
$selectedClientDist=if($ClientDistPath) {[IO.Path]::GetFullPath($ClientDistPath)} else {Join-Path $clientRoot 'dist'}
if($selectedClientDist.IndexOfAny([char[]]@([char]34,[char]10,[char]13)) -ge 0 -or -not $selectedClientDist.StartsWith($projectRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'The client export must be a directory inside this workspace.'}
if($ClientDistPath -and -not $SkipBuild) {throw 'An explicit client export requires -SkipBuild so its verified bytes are preserved.'}
$outputRoot=Join-Path $backendRoot ('target\'+$OutputName)
$jdkCandidates=@(
    $env:PULSE_JDK21_HOME,
    $env:JAVA_HOME,
    'C:\Program Files\Android\Android Studio1\jbr',
    'C:\Program Files\Android\Android Studio\jbr'
) | Where-Object {-not [string]::IsNullOrWhiteSpace($_)} | Select-Object -Unique
$jdkRoot=$null
foreach($candidate in $jdkCandidates) {
    $candidateRoot=[IO.Path]::GetFullPath($candidate)
    $candidateJava=Join-Path $candidateRoot 'bin\java.exe'
    $candidateJavac=Join-Path $candidateRoot 'bin\javac.exe'
    if(-not (Test-Path -LiteralPath $candidateJava) -or -not (Test-Path -LiteralPath $candidateJavac)) {continue}
    $releaseFile=Join-Path $candidateRoot 'release'
    if(-not (Test-Path -LiteralPath $releaseFile)) {continue}
    $releaseVersion=(Get-Content -LiteralPath $releaseFile | Where-Object {$_ -match '^JAVA_VERSION='} | Select-Object -First 1)
    if($releaseVersion -match '^JAVA_VERSION="21\.') {$jdkRoot=$candidateRoot;break}
}
if(-not $jdkRoot) {throw 'A full Java 21 JDK was not found. Set PULSE_JDK21_HOME or JAVA_HOME to its installation folder; Java 25 on PATH is not sufficient.'}
$rtkPath=(Get-Command rtk -ErrorAction Stop).Source
$mavenPath='C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
if(-not (Test-Path -LiteralPath $mavenPath)) {throw 'The verified Maven runtime is unavailable.'}
if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {throw 'The requested local port is already occupied; choose another port. Existing processes are preserved.'}
$priorJava=$env:JAVA_HOME
$priorDemo=$env:PROFESSOR_DEMO_ENABLED
$priorPassword=$env:PROFESSOR_DEMO_PASSWORD
$priorPort=$env:PROFESSOR_DEMO_PORT
try {
    $env:JAVA_HOME=$jdkRoot
    if(-not $SkipBuild) {
        Push-Location $clientRoot
        try {
            & $rtkPath npm run typecheck
            if($LASTEXITCODE -ne 0) {throw 'Shared typecheck failed.'}
            & $rtkPath npm test
            if($LASTEXITCODE -ne 0) {throw 'Shared tests failed.'}
            & $rtkPath npm run build:web
            if($LASTEXITCODE -ne 0) {throw 'Web export failed.'}
        } finally {Pop-Location}
        & $rtkPath proxy $mavenPath -q -f (Join-Path $backendRoot 'pom.xml') verify
        if($LASTEXITCODE -ne 0) {throw 'Backend verification failed.'}
    }
    $jars=@(Get-Item -LiteralPath (Join-Path $backendRoot ('target\'+$ArtifactName)) -ErrorAction SilentlyContinue)
    if($jars.Count -ne 1 -or -not (Test-Path -LiteralPath (Join-Path $selectedClientDist 'index.html') -PathType Leaf)) {throw 'Build the current backend and web artifacts before starting.'}
    $sourceFiles=@(Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src') -Recurse -File)
    $latestSource=($sourceFiles | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
    if($jars[0].LastWriteTimeUtc -lt $latestSource) {throw 'The backend artifact is older than its source; rerun without -SkipBuild.'}
    New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
    $randomBytes=New-Object byte[] 24
    $generator=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {$generator.GetBytes($randomBytes)} finally {$generator.Dispose()}
    $demoPassword='Demo1!'+[Convert]::ToBase64String($randomBytes)
    $env:PROFESSOR_DEMO_ENABLED='true'
    $env:PROFESSOR_DEMO_PASSWORD=$demoPassword
    $env:PROFESSOR_DEMO_PORT=[string]$Port
    $credentialsPath=Join-Path $outputRoot 'session-credentials.json'
    $credentialRecord=@{mode='SYNTHETIC_LOCAL_REVIEW';url="http://127.0.0.1:$Port/app/";password=$demoPassword;accounts=@('student@example.invalid','finder@example.invalid','applicant@example.invalid','faculty@example.invalid','reviewer@example.invalid','admin@example.invalid','warden@example.invalid')}
    $javaPath=Join-Path $jdkRoot 'bin\java.exe'
    # Preserve the Java environment guard's exact generated-path boundary. A
    # separate review run gets its own working directory and nested demo tree.
    $runtimeRoot=if($OutputName -eq 'professor-demo') {$backendRoot} else {$outputRoot}
    $runtimeMediaRoot=Join-Path $runtimeRoot 'target\professor-demo'
    # A short explicit socket directory avoids the observed Windows TEMP remapping
    # failure in JDK PipeImpl. This changes only this JVM, not OS security settings.
    $socketRoot='C:\tools\pulse-jvm-sockets'
    New-Item -ItemType Directory -Path $socketRoot -Force | Out-Null
    # Bound this synthetic H2 demo JVM so verification does not consume the host's default heap allowance.
    $arguments='proxy "'+$javaPath+'" -Xms64m -Xmx512m -XX:MaxMetaspaceSize=256m "-Djdk.net.unixdomain.tmpdir='+$socketRoot+'" -jar "'+$jars[0].FullName+'" --spring.profiles.active=professor-demo "--app.upload.dir='+(Join-Path $runtimeMediaRoot 'uploads')+'" "--logging.file.name='+(Join-Path $runtimeMediaRoot 'logs\backend.log')+'" "--app.client.dist='+$selectedClientDist+'"'
    $process=Start-Process -FilePath $rtkPath -ArgumentList $arguments -WorkingDirectory $runtimeRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $outputRoot 'stdout.log') -RedirectStandardError (Join-Path $outputRoot 'stderr.log')
    $startedAt=[DateTime]::UtcNow.ToString('o')
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++) {
        $process.Refresh()
        if($process.HasExited) {throw 'Demo startup failed. Inspect dedicated stdout/stderr logs; historical databases were not selected.'}
        try {
            $response=Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/ready" -UseBasicParsing -TimeoutSec 2
            if($response.StatusCode -eq 200) {
                $listeners=@(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop)
                $children=@(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.Id)" -ErrorAction Stop)
                $ownedIds=@($process.Id)+@($children | Where-Object {$_.ExecutablePath -eq $javaPath} | ForEach-Object {$_.ProcessId})
                $owned=@($listeners | Where-Object {$ownedIds -contains $_.OwningProcess})
                if($owned.Count -gt 0) {$ready=$true;$javaProcessId=[int]$owned[0].OwningProcess;break}
            }
        } catch { }
        Start-Sleep -Milliseconds 1000
    }
    if(-not $ready) {throw 'Demo readiness was not confirmed within the startup window. Inspect the dedicated logs.'}
    # Publish credentials and process identity only after this run owns the listener.
    $credentialRecord | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $credentialsPath -Encoding UTF8
    @{pid=$process.Id;javaPid=$javaProcessId;port=$Port;jar=$jars[0].FullName;clientDist=$selectedClientDist;startedAt=$startedAt} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot 'process.json') -Encoding UTF8
    Write-Output "Synthetic professor demo: http://127.0.0.1:$Port/app/"
    Write-Output "Local demo credentials: $credentialsPath"
    Write-Output 'Use only synthetic examples. Restart resets the demo database; this is not a production launch.'
} finally {
    $env:JAVA_HOME=$priorJava
    $env:PROFESSOR_DEMO_ENABLED=$priorDemo
    $env:PROFESSOR_DEMO_PASSWORD=$priorPassword
    $env:PROFESSOR_DEMO_PORT=$priorPort
}
