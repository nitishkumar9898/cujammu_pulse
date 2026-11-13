param(
    [string]$ArtifactPath='',
    [string]$BuildEvidencePath='',
    [string]$JavaAuditPath='',
    [string]$NpmAuditPath='',
    [string]$ClientEvidencePath='',
    [string]$EvidenceManifestPath='',
    [string]$OutputPath=''
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$target=Join-Path $root 'college-events\target'
if(-not $ArtifactPath) {$ArtifactPath=Join-Path $target 'events-security-followup.jar'}
if(-not $BuildEvidencePath) {$BuildEvidencePath=Join-Path $target 'security-isolated-followup-evidence.json'}
if(-not $JavaAuditPath) {$JavaAuditPath=Join-Path $target 'java-security-audit-followup-2026-10-19.json'}
if(-not $NpmAuditPath) {$NpmAuditPath=Join-Path $target 'npm-security-audit-followup-2026-10-19.json'}
if(-not $ClientEvidencePath) {$ClientEvidencePath=Join-Path $target 'client-launch-followup-evidence.json'}
if(-not $OutputPath) {$OutputPath=Join-Path $target 'production-release-gate.json'}
$checks=[Collections.Generic.List[object]]::new()
function Record([string]$id,[bool]$passed,[string]$reason) {
    $checks.Add([pscustomobject]@{id=$id;passed=$passed;reason=$reason})
}
function Digest([string]$path) {
    $sha=[Security.Cryptography.SHA256]::Create()
    $stream=[IO.File]::OpenRead($path)
    try {return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','')}
    finally {$stream.Dispose();$sha.Dispose()}
}
function Json([string]$path) {return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json}
function Recent([string]$date) {
    $parsed=[DateTimeOffset]::Parse($date).ToUniversalTime()
    $age=[DateTimeOffset]::UtcNow-$parsed
    return $age.TotalSeconds -ge -300 -and $age.TotalDays -le 7
}
$artifactHash=''
try {$artifactHash=Digest $ArtifactPath;Record 'artifact' $true 'Artifact bytes are present and SHA-256 recorded.'}
catch {Record 'artifact' $false 'Artifact missing or unreadable.'}
$sourceManifestHash=''
try {
    $build=Json $BuildEvidencePath
    $valid=$build.exitCode -eq 0 -and -not $build.filter -and $build.totals.tests -gt 0 -and $build.suites -gt 0 -and $build.totals.failures -eq 0 -and $build.totals.errors -eq 0 -and $build.totals.skipped -eq 0 -and @($build.changedSinceSnapshot).Count -eq 0 -and $artifactHash -and $build.artifactSha256 -eq $artifactHash -and (Recent $build.completedAt)
    $sourceManifestPath=Join-Path $build.snapshot 'source-manifest.json'
    # Windows PowerShell 5.1 ConvertFrom-Json emits a root JSON array as one
    # pipeline object. Assign first so @() does not wrap that array again.
    $manifest=Json $sourceManifestPath
    $manifest=@($manifest)
    if($manifest.Count -eq 0) {$valid=$false}
    $sourceManifestHash=Digest $sourceManifestPath
    $source=Join-Path $root 'college-events'
    $expected=@{}
    foreach($item in $manifest) {
        $file=[IO.Path]::GetFullPath((Join-Path $source $item.path))
        if(-not $file.StartsWith($source+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Invalid manifest path'}
        if($expected.ContainsKey($item.path)) {throw 'Duplicate manifest path'}
        $expected[$item.path]=$true
        if((Digest $file) -ne $item.sha256) {$valid=$false}
    }
    $current=@(Get-Item (Join-Path $source 'pom.xml'))+@(Get-ChildItem (Join-Path $source 'src') -Recurse -File)
    if($current.Count -ne $manifest.Count) {$valid=$false}
    foreach($item in $current) {if(-not $expected.ContainsKey($item.FullName.Substring($source.Length+1))) {$valid=$false}}
    Record 'full-build-source-identity' $valid 'Requires fresh full successful build, no skips/drift, matching artifact and complete current source inventory.'
} catch {Record 'full-build-source-identity' $false 'Full build evidence/source manifest missing, invalid or unreadable.'}
try {
    $audit=Json $JavaAuditPath
    $valid=$artifactHash -and $audit.artifactSha256 -eq $artifactHash -and $audit.packagesChecked -gt 0 -and @($audit.findings).Count -eq 0 -and @($audit.unidentifiedJars).Count -eq 0 -and (Recent $audit.timestamp)
    Record 'java-dependencies' $valid 'Requires artifact-matched fresh audit, complete inventory and zero unresolved matches; conditional matches need actual resolution before this gate.'
} catch {Record 'java-dependencies' $false 'Current artifact dependency audit unavailable or invalid.'}
try {
    $audit=Json $NpmAuditPath
    $hasErrors=$null -ne $audit.error
    $counts=$audit.metadata.vulnerabilities
    $typedCounts=$null -ne $counts -and ($counts.high -is [int] -or $counts.high -is [long]) -and ($counts.critical -is [int] -or $counts.critical -is [long])
    $client=Json $ClientEvidencePath
    $bound=$client.npmAuditSha256 -eq (Digest $NpmAuditPath) -and $client.lockfileSha256 -eq (Digest (Join-Path $root 'cujammu-pulse\package-lock.json')) -and (Recent $client.npmAuditedAt)
    $valid=-not $hasErrors -and $audit.auditReportVersion -eq 2 -and $typedCounts -and $counts.high -eq 0 -and $counts.critical -eq 0 -and $bound
    Record 'npm-dependencies' $valid 'Requires typed High/Critical counts of zero, successful audit and hash/time binding to the exact current lockfile; provenance remains subject to review.'
} catch {Record 'npm-dependencies' $false 'npm audit unavailable or invalid.'}
$persistenceGate=Join-Path $root 'college-events\src\main\java\com\college\events\config\ProductionPersistenceGateConfiguration.java'
Record 'production-persistence-implementation' (-not (Test-Path -LiteralPath $persistenceGate)) 'Current deliberate production startup block must remain until approved Mongo adapter/cutover is implemented; deleting this file alone does not satisfy reviewed Mongo evidence.'
$required=@('mongo-migration-concurrency','client-web-android-parity','tls-edge-identity-mfa','load-soak-distributed-abuse','private-media-backup-restore','security-scans-sbom-signing','independent-deployed-assessment','privacy-policy-office-uat','incident-alert-dr-owners','ai-provider-adversarial-evaluation','university-release-approval')
$external=$null
try {
    if(-not $EvidenceManifestPath) {throw 'Evidence manifest absent'}
    $external=Json $EvidenceManifestPath
    if($external.schemaVersion -ne 1 -or $external.artifactSha256 -ne $artifactHash -or -not $artifactHash -or $external.sourceManifestSha256 -ne $sourceManifestHash -or -not $sourceManifestHash -or [string]::IsNullOrWhiteSpace($external.deploymentTarget)) {throw 'Manifest identity invalid'}
    Record 'external-evidence-manifest' $true 'Artifact/source identity and named deployment target supplied; provenance requires human verification.'
} catch {$external=$null;Record 'external-evidence-manifest' $false 'Reviewed external evidence manifest absent or identity invalid.'}
foreach($id in $required) {
    try {
        $records=@($external.evidence | Where-Object {$_.id -eq $id})
        if($records.Count -ne 1) {throw 'Evidence record absent or duplicated'}
        $entry=$records[0]
        $valid=$entry.status -eq 'satisfied' -and -not [string]::IsNullOrWhiteSpace($entry.reviewer) -and -not [string]::IsNullOrWhiteSpace($entry.reviewerRole) -and -not [string]::IsNullOrWhiteSpace($entry.scope) -and -not [string]::IsNullOrWhiteSpace($entry.version) -and -not [string]::IsNullOrWhiteSpace($entry.authorityReference) -and (Recent $entry.reviewedAt) -and [DateTimeOffset]::Parse($entry.validUntil) -gt [DateTimeOffset]::UtcNow
        if($entry.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or (Digest $entry.path) -ne $entry.sha256) {$valid=$false}
        Record $id $valid 'Requires named authorized reviewer, role, scoped versioned review, authority reference, current review/expiry and hash-matched evidence; contents/provenance need human review.'
    } catch {Record $id $false 'Required reviewed evidence absent, expired, duplicated, unreadable or incomplete.'}
}
$blocked=@($checks | Where-Object {-not $_.passed})
$report=[ordered]@{schemaVersion=1;generatedAt=[DateTime]::UtcNow.ToString('o');status=$(if($blocked.Count) {'BLOCKED'} else {'EVIDENCE_PRESENT_REQUIRES_HUMAN_RELEASE_APPROVAL'});artifactSha256=$artifactHash;sourceManifestSha256=$sourceManifestHash;blockedChecks=$blocked.Count;checks=@($checks);assurance='Offline integrity/completeness check only. It does not authenticate signers, reproduce tests, certify assessment or authorize deployment.'}
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($OutputPath))) -Force | Out-Null
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output ($report.status+': '+$blocked.Count+' incomplete gates. Report: '+$OutputPath)
foreach($check in $blocked) {Write-Output ('BLOCKED '+$check.id)}
if($blocked.Count) {exit 2}
exit 0
