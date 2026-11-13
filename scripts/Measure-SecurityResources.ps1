param([ValidateRange(1024,65535)][int]$Port=18081,[ValidateRange(5,300)][int]$Seconds=45,
    [ValidatePattern('^[a-z][a-z0-9-]{0,63}$')][string]$OutputName='security-review-2026-10-19')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$listener=Get-NetTCPConnection -State Listen -LocalPort $Port -LocalAddress '127.0.0.1' -ErrorAction Stop | Select-Object -First 1
$serverProcessId=[int]$listener.OwningProcess
$server=Get-CimInstance Win32_Process -Filter "ProcessId=$serverProcessId"
$metadata=Get-Content -LiteralPath (Join-Path $root ('college-events\target\'+$OutputName+'\process.json')) -Raw | ConvertFrom-Json
$expectedJar=[IO.Path]::GetFullPath([string]$metadata.jar)
$allowedRoot=[IO.Path]::GetFullPath((Join-Path $root 'college-events\target'))+[IO.Path]::DirectorySeparatorChar
if(-not $expectedJar.StartsWith($allowedRoot,[StringComparison]::OrdinalIgnoreCase) -or [int]$metadata.port -ne $Port) {throw 'Sampling metadata is outside the selected synthetic run.'}
if(-not $server.CommandLine.Contains($expectedJar) -or -not $server.CommandLine.Contains($OutputName)) {throw 'Resource sampling requires the isolated security review JVM.'}
$samples=[Collections.Generic.List[object]]::new()
$started=Get-Date
do {
    $process=Get-Process -Id $serverProcessId -ErrorAction Stop
    $samples.Add([pscustomobject]@{elapsedMs=[math]::Round(((Get-Date)-$started).TotalMilliseconds);cpuSeconds=$process.CPU;workingSetBytes=$process.WorkingSet64;privateBytes=$process.PrivateMemorySize64})
    Start-Sleep -Milliseconds 500
} while(((Get-Date)-$started).TotalSeconds -lt $Seconds)
$elapsed=($samples[-1].elapsedMs-$samples[0].elapsedMs)/1000
$cpu=$samples[-1].cpuSeconds-$samples[0].cpuSeconds
$report=[pscustomobject]@{scope='Synthetic local Java process including read workload, browser bootstrap and idle; H2 in process; no capacity certification';startedAt=$started.ToUniversalTime().ToString('o');completedAt=[DateTime]::UtcNow.ToString('o');processId=$serverProcessId;durationSeconds=$elapsed;cpuSeconds=$cpu;cpuPercentOneLogicalCore=[math]::Round(100*$cpu/$elapsed,2);peakSampledWorkingSetMiB=[math]::Round(($samples|Measure-Object -Property workingSetBytes -Maximum).Maximum/1MB,2);peakSampledPrivateMiB=[math]::Round(($samples|Measure-Object -Property privateBytes -Maximum).Maximum/1MB,2);samples=@($samples)}
$output=if($OutputName -eq 'security-review-2026-10-19') {Join-Path $root 'college-events\target\security-resource-sample-2026-10-19.json'} else {Join-Path $root ('college-events\target\'+$OutputName+'\resource-sample.json')}
$report|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $output -Encoding UTF8
$report|Select-Object scope,durationSeconds,cpuSeconds,cpuPercentOneLogicalCore,peakSampledWorkingSetMiB,peakSampledPrivateMiB|ConvertTo-Json -Compress
