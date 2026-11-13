param([string]$Tests='',[switch]$Package,
    [ValidateRange(512,2048)][int]$TestJvmHeapMiB=1024,
    [ValidateRange(1,8)][int]$TestContextCacheMaxSize=4,
    [string]$JdkPath='',
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._-]*\.jar$')][string]$ArtifactName='events-security-followup.jar',
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._-]*\.json$')][string]$EvidenceName='security-isolated-followup-evidence.json')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source=Join-Path $root 'college-events'
$snapshot=Join-Path 'C:\tools' ('pulse-security-build-'+[Guid]::NewGuid().ToString('N'))
$jdkCandidates=@($JdkPath,$env:PULSE_JDK21_HOME,$env:JAVA_HOME,'C:\Program Files\Android\Android Studio\jbr') | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'release')) }
$jdk=$jdkCandidates | Where-Object { (Get-Content -LiteralPath (Join-Path $_ 'release')) -match '^JAVA_VERSION="21\.' } | Select-Object -First 1
if(-not $jdk) {throw 'A full Java 21 JDK is required. Use -JdkPath or set PULSE_JDK21_HOME.'}
$maven='C:\tools\apache-maven-3.9.9\bin\mvn.cmd'
$sockets='C:\tools\pulse-security-sockets'
function Digest([string]$path) {
    $algorithm=[Security.Cryptography.SHA256]::Create()
    $inputStream=[IO.File]::OpenRead($path)
    try { return [BitConverter]::ToString($algorithm.ComputeHash($inputStream)).Replace('-','') }
    finally {$inputStream.Dispose();$algorithm.Dispose()}
}
New-Item -ItemType Directory -Path $snapshot,$sockets -Force | Out-Null
$manifest=@()
$files=@(Get-Item (Join-Path $source 'pom.xml'))+@(Get-ChildItem (Join-Path $source 'src') -Recurse -File)
foreach($file in $files) {
    $relative=$file.FullName.Substring($source.Length+1)
    $before=Digest $file.FullName
    $destination=Join-Path $snapshot $relative
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $destination
    if((Digest $destination) -ne $before -or (Digest $file.FullName) -ne $before) {throw 'Source changed during snapshot; retry after edits finish.'}
    $manifest += [pscustomobject]@{path=$relative;sha256=$before}
}
$manifest | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $snapshot 'source-manifest.json') -Encoding UTF8
Write-Output ("Isolated source snapshot: "+$snapshot+"; files: "+$manifest.Count)
$previousJava=$env:JAVA_HOME
$previousMavenOptions=$env:MAVEN_OPTS
$started=[DateTime]::UtcNow
try {
    $env:JAVA_HOME=$jdk
    $env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m'
    Push-Location $snapshot
    try {
        $arguments=@('-q','-f',(Join-Path $snapshot 'pom.xml'),"-DargLine=-Xmx$($TestJvmHeapMiB)m -XX:MaxMetaspaceSize=384m -Dspring.test.context.cache.maxSize=$TestContextCacheMaxSize -Djdk.net.unixdomain.tmpdir=$sockets")
        if($Tests) {$arguments += "-Dtest=$Tests"}
        $arguments += $(if($Package) {'verify'} else {'test'})
        $savedErrorPreference=$ErrorActionPreference
        try {
            $ErrorActionPreference='Continue'
            & rtk proxy $maven @arguments *> (Join-Path $snapshot 'verification.log')
            $resultCode=$LASTEXITCODE
        } finally {$ErrorActionPreference=$savedErrorPreference}
    } finally {Pop-Location}
    $totals=@{tests=0;failures=0;errors=0;skipped=0}
    $reportDirectory=Join-Path $snapshot 'target\surefire-reports'
    $reports=@()
    if(Test-Path -LiteralPath $reportDirectory) {$reports=@(Get-ChildItem $reportDirectory -Filter 'TEST-*.xml')}
    foreach($report in $reports) {
        [xml]$xml=Get-Content -LiteralPath $report.FullName
        foreach($key in @('tests','failures','errors','skipped')) {$totals[$key]+=[int]$xml.testsuite.GetAttribute($key)}
    }
    $changed=@($manifest | Where-Object { -not (Test-Path -LiteralPath (Join-Path $source $_.path)) -or (Digest (Join-Path $source $_.path)) -ne $_.sha256} | ForEach-Object {$_.path})
    $currentFiles=@(Get-Item (Join-Path $source 'pom.xml'))+@(Get-ChildItem (Join-Path $source 'src') -Recurse -File)
    $added=@($currentFiles | ForEach-Object {$_.FullName.Substring($source.Length+1)} | Where-Object {$_ -notin $manifest.path})
    $changed += $added
    $evidence=@{startedAt=$started.ToString('o');completedAt=[DateTime]::UtcNow.ToString('o');snapshot=$snapshot;filter=$Tests;exitCode=$resultCode;totals=$totals;suites=$reports.Count;changedSinceSnapshot=$changed;socketTempDirectory=$sockets;testJvmHeapMiB=$TestJvmHeapMiB;testJvmMetaspaceMiB=384;testContextCacheMaxSize=$TestContextCacheMaxSize}
    if($Package -and $resultCode -eq 0) {
        $artifacts=@(Get-ChildItem (Join-Path $snapshot 'target') -Filter '*.jar' -File | Where-Object {$_.Name -notmatch '(sources|javadoc)\.jar$'})
        if($artifacts.Count -ne 1) {throw 'Expected exactly one isolated application JAR.'}
        $jar=$artifacts[0].FullName
        $evidence.artifactSha256=Digest $jar
        $evidence.artifact=$jar
        $copiedArtifact=Join-Path $source ('target\'+$ArtifactName)
        Copy-Item -LiteralPath $jar -Destination $copiedArtifact
        $evidence.copiedArtifactSha256=Digest $copiedArtifact
        if($evidence.copiedArtifactSha256 -ne $evidence.artifactSha256) {throw 'Copied artifact identity mismatch.'}
    }
    $evidence | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $source ('target\'+$EvidenceName)) -Encoding UTF8
    $evidence | ConvertTo-Json -Depth 5
    if($resultCode -ne 0) {throw "Isolated verification failed; inspect $snapshot\verification.log"}
} finally {$env:JAVA_HOME=$previousJava;$env:MAVEN_OPTS=$previousMavenOptions}
