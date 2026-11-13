param([ValidatePattern('^[a-z][a-z0-9-]{0,63}$')][string]$OutputName='professor-demo')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$metadataPath=Join-Path $projectRoot ('college-events\target\'+$OutputName+'\process.json')
if(-not (Test-Path -LiteralPath $metadataPath)) {Write-Output 'No local demo process metadata exists.';exit 0}
$metadata=Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$demoProcessId=[int]$metadata.pid
$expectedJar=[IO.Path]::GetFullPath([string]$metadata.jar)
$allowedTarget=[IO.Path]::GetFullPath((Join-Path $projectRoot 'college-events\target'))+[IO.Path]::DirectorySeparatorChar
if(-not $expectedJar.StartsWith($allowedTarget,[StringComparison]::OrdinalIgnoreCase)) {throw 'Process metadata is outside the intended demo build directory.'}
$candidates=@(Get-CimInstance Win32_Process -Filter "ProcessId=$demoProcessId")+@(Get-CimInstance Win32_Process -Filter "ParentProcessId=$demoProcessId")
$matches=@($candidates | Where-Object {$_.CommandLine -and $_.CommandLine.Contains($expectedJar) -and $_.CommandLine.Contains('--spring.profiles.active=professor-demo')})
if($matches.Count -eq 0) {Write-Output 'No matching synthetic demo process remains. Other processes are preserved.';exit 0}
foreach($candidate in ($matches | Sort-Object @{Expression={$_.Name -eq 'java.exe'};Descending=$true})) {
    Stop-Process -Id $candidate.ProcessId -ErrorAction SilentlyContinue
}
Write-Output 'The matching local synthetic demo was stopped. Generated evidence and historical workspace data are preserved.'
