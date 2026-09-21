. "$PSScriptRoot/common.ps1"
foreach ($name in @('frontend', 'backend')) {
    $state = Join-Path $Runtime "$name.json"
    if (-not (Test-Path -LiteralPath $state)) { Write-Host "$name has no managed PID; left untouched."; continue }
    $entry = Get-Content -Raw -LiteralPath $state -Encoding UTF8 | ConvertFrom-Json
    $process = Get-OwnedProcess $entry
    if ($process) { Stop-Process -Id $process.ProcessId -ErrorAction Stop; Write-Host "Stopped $name PID $($process.ProcessId)." }
    Remove-Item -LiteralPath $state
}
Write-Host 'PostgreSQL and Redis containers/volumes retained.'
