param([string]$JarPath = '', [string]$OutputPath = '', [string]$InventoryPath = '')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $JarPath) {$JarPath=Join-Path $root 'college-events\target\events-1.0.0.jar'}
if(-not $OutputPath) {$OutputPath=Join-Path $root 'college-events\target\java-security-audit-2026-10-19.json'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[xml]$projectPom=Get-Content -LiteralPath (Join-Path $root 'college-events\pom.xml')
$bootVersion=[string]$projectPom.project.parent.version
$archive=[IO.Compression.ZipFile]::OpenRead($JarPath)
$packages=[Collections.Generic.List[object]]::new()
$unidentified=[Collections.Generic.List[string]]::new()
$resolvedByFile=@{}
$inventoryPath=if($InventoryPath) {[IO.Path]::GetFullPath($InventoryPath)} else {Join-Path $root 'college-events\target\security-dependencies.txt'}
if(Test-Path -LiteralPath $inventoryPath) {
    foreach($line in Get-Content -LiteralPath $inventoryPath) {
        $clean=[regex]::Replace($line,"$([char]27)\[[0-9;]*m",'')
        # dependency:tree prefixes each level with `+-`, `\\-`, and `|`.
        # Normalize those before applying the same strict coordinate grammar
        # used for the older flat dependency:list inventory.
        $clean=[regex]::Replace($clean,'^[\s|+\\-]+','')
        $match=[regex]::Match($clean,'^\s*(?<group>[^:\s]+):(?<artifact>[^:\s]+):jar:(?:(?<classifier>[^:\s]+):)?(?<version>[^:\s]+):(?:compile|runtime)(?:\s|$)')
        if($match.Success) {
            $filename=$match.Groups['artifact'].Value+'-'+$match.Groups['version'].Value
            if($match.Groups['classifier'].Success) {$filename+='-'+$match.Groups['classifier'].Value}
            $filename+='.jar'
            $coordinate=[pscustomobject]@{name=$match.Groups['group'].Value+':'+$match.Groups['artifact'].Value;version=$match.Groups['version'].Value;jar=$filename}
            if($resolvedByFile.ContainsKey($filename) -and $resolvedByFile[$filename].name -ne $coordinate.name) {throw 'Ambiguous resolved dependency filename.'}
            $resolvedByFile[$filename]=$coordinate
        }
    }
}
try {
    foreach($entry in $archive.Entries | Where-Object {$_.FullName -like 'BOOT-INF/lib/*.jar'}) {
        $buffer=[IO.MemoryStream]::new()
        $stream=$entry.Open()
        try {$stream.CopyTo($buffer)} finally {$stream.Dispose()}
        $buffer.Position=0
        $nested=[IO.Compression.ZipArchive]::new($buffer,[IO.Compression.ZipArchiveMode]::Read)
        try {
            $metadata=@($nested.Entries | Where-Object {$_.FullName -match '^META-INF/maven/[^/]+/[^/]+/pom.properties$'})
            $validCoordinates=0
            foreach($property in $metadata) {
                $reader=[IO.StreamReader]::new($property.Open())
                try {$content=$reader.ReadToEnd()} finally {$reader.Dispose()}
                $group=[regex]::Match($content,'(?m)^groupId=(.+)\r?$').Groups[1].Value.Trim()
                $artifact=[regex]::Match($content,'(?m)^artifactId=(.+)\r?$').Groups[1].Value.Trim()
                $version=[regex]::Match($content,'(?m)^version=(.+)\r?$').Groups[1].Value.Trim()
                if($group -and $artifact -and $version) {$packages.Add([pscustomobject]@{name="$($group):$artifact";version=$version;jar=$entry.Name});$validCoordinates++}
            }
            if($validCoordinates -eq 0) {
                if($resolvedByFile.ContainsKey($entry.Name)) {$packages.Add($resolvedByFile[$entry.Name])}
                elseif($entry.Name -eq "spring-boot-jarmode-tools-$bootVersion.jar") {
                    # Boot's pinned packager adds this tooling JAR outside the
                    # application dependency list. Require a byte-for-byte match
                    # with its resolved Maven artifact before assigning identity.
                    $localTool=Join-Path $env:USERPROFILE ('.m2\repository\org\springframework\boot\spring-boot-jarmode-tools\'+$bootVersion+'\'+$entry.Name)
                    $identified=$false
                    if(Test-Path -LiteralPath $localTool) {
                        $toolHasher=[Security.Cryptography.SHA256]::Create();$toolStream=[IO.File]::OpenRead($localTool)
                        try {
                            $localHash=[BitConverter]::ToString($toolHasher.ComputeHash($toolStream))
                            $buffer.Position=0
                            $embeddedHash=[BitConverter]::ToString($toolHasher.ComputeHash($buffer))
                            if($localHash -eq $embeddedHash) {
                                $packages.Add([pscustomobject]@{name='org.springframework.boot:spring-boot-jarmode-tools';version=$bootVersion;jar=$entry.Name})
                                $identified=$true
                            }
                        } finally {$toolStream.Dispose();$toolHasher.Dispose()}
                    }
                    if(-not $identified) {$unidentified.Add($entry.Name)}
                } else {$unidentified.Add($entry.Name)}
            }
        } finally {$nested.Dispose();$buffer.Dispose()}
    }
} finally {$archive.Dispose()}
$unique=@($packages | Sort-Object -Property name,version -Unique)
if($unique.Count -eq 0) {throw 'No Maven coordinates could be inspected; the dependency audit is incomplete.'}
$findings=[Collections.Generic.List[object]]::new()
for($offset=0;$offset -lt $unique.Count;$offset+=100) {
    $batch=@($unique | Select-Object -Skip $offset -First 100)
    $queries=@($batch | ForEach-Object {@{package=@{name=$_.name;ecosystem='Maven'};version=$_.version}})
    # Only public dependency coordinates are sent; no source, records or secrets.
    $response=Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' -Body (@{queries=$queries}|ConvertTo-Json -Depth 6 -Compress) -TimeoutSec 30
    if($response.error -or @($response.results).Count -ne $batch.Count) {throw 'Vulnerability service returned an incomplete result set.'}
    for($index=0;$index -lt $batch.Count;$index++) {
        foreach($vulnerability in $response.results[$index].vulns) {
            $findings.Add(@{name=$batch[$index].name;version=$batch[$index].version;id=$vulnerability.id;source="https://osv.dev/vulnerability/$($vulnerability.id)"})
        }
    }
}
$hasher=[Security.Cryptography.SHA256]::Create()
$jarStream=[IO.File]::OpenRead([IO.Path]::GetFullPath($JarPath))
try {$digest=[BitConverter]::ToString($hasher.ComputeHash($jarStream)).Replace('-','')} finally {$jarStream.Dispose();$hasher.Dispose()}
$report=@{timestamp=[DateTime]::UtcNow.ToString('o');source='OSV live querybatch';artifactSha256=$digest;packagesChecked=$unique.Count;unidentifiedJars=@($unidentified);findings=@($findings);inventory=$unique}
$report|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $OutputPath -Encoding UTF8
Write-Output "Java public dependency metadata checked: $($unique.Count); advisory matches: $($findings.Count); jars without Maven metadata: $($unidentified.Count)."
Write-Output "Evidence: $OutputPath"
if($findings.Count -gt 0 -or $unidentified.Count -gt 0) {exit 1}
