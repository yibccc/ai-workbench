$ErrorActionPreference = 'Stop'
$statePath = Join-Path $env:TEMP 'focus-manual-runtime.json'
if (-not (Test-Path -LiteralPath $statePath)) { throw 'Manual acceptance runtime record is missing.' }
$state = Get-Content -Raw -Encoding UTF8 $statePath | ConvertFrom-Json
foreach ($entry in @(@{ id = [int]$state.frontendPid; path = [string]$state.frontendPath },
                     @{ id = [int]$state.backendPid; path = [string]$state.backendPath })) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.id)"
    if (-not $process) { continue }
    if ($process.CommandLine -notlike "*$($entry.path)*") {
        throw "PID $($entry.id) no longer belongs to the manual acceptance service."
    }
    Stop-Process -Id $entry.id -Force
}
Remove-Item -LiteralPath $statePath -Force
'Stopped only the recorded manual acceptance services.'
