param(
    [Parameter(Mandatory = $true)][string]$StateFile,
    [Parameter(Mandatory = $true)][string]$EnvFile,
    [switch]$ValidateOnly
)
. "$PSScriptRoot/common.ps1"
$resolvedState = (Resolve-Path -LiteralPath $StateFile).Path
$runtimePrefix = [System.IO.Path]::GetFullPath($Runtime).TrimEnd('\') + '\'
if (-not $resolvedState.StartsWith($runtimePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Writer state must be an explicit file below this repository .local-runtime.'
}
$entry = Get-Content -Raw -LiteralPath $resolvedState -Encoding UTF8 | ConvertFrom-Json
foreach ($key in @('service', 'moduleDirectory', 'directory', 'workingDirectory', 'envFile', 'envFileSha256', 'databaseIdentity', 'captureError')) {
    if ($key -notin $entry.PSObject.Properties.Name) { throw 'Writer state does not contain launch-time capture; no process was stopped.' }
}
if ($entry.service -ne 'backend' -or $entry.moduleDirectory -ne (Join-Path $RepoRoot 'backend') -or
    $entry.directory -ne $RepoRoot -or $entry.workingDirectory -ne $RepoRoot) { throw 'State does not describe this repository backend launch.' }
if ($null -eq $entry.databaseIdentity -or $entry.captureError) { throw 'Writer database capture was not confirmed at launch; no process was stopped.' }
$resolvedEnv = (Resolve-Path -LiteralPath $EnvFile).Path
if ($entry.envFile -ne $resolvedEnv -or $entry.envFileSha256 -ne (Get-FileHash -LiteralPath $resolvedEnv -Algorithm SHA256).Hash.ToLowerInvariant()) {
    throw 'Writer environment identity changed or was not recorded at launch.'
}
$effective = [Environment]::GetEnvironmentVariables('Process')
$envValues = Get-EnvValues -EnvFile $resolvedEnv
foreach ($key in $envValues.Keys) { $effective[$key] = $envValues[$key] }
$expectedIdentity = Get-BackendDatabaseIdentity -Values $effective
foreach ($key in @('host', 'port', 'database', 'schema')) {
    if ($entry.databaseIdentity.$key -cne $expectedIdentity[$key]) { throw 'Writer database identity does not match its launch environment.' }
}
if ($entry.databaseIdentity.host -notin @('localhost', '127.0.0.1') -or
    $entry.databaseIdentity.port -lt 1 -or $entry.databaseIdentity.port -gt 65535 -or
    $entry.databaseIdentity.database -notmatch '^[a-z_][a-z0-9_]{0,62}$' -or
    $entry.databaseIdentity.schema -notmatch '^[a-z_][a-z0-9_]{0,62}$') {
    throw 'Explicit writer database identity is required.'
}
$process = Get-OwnedProcess $entry
if ($ValidateOnly) { Write-Host 'Owned backend writer identity verified; no process stopped.'; return }
if ($process) { Stop-Process -Id $process.ProcessId -ErrorAction Stop }
Remove-Item -LiteralPath $resolvedState
Write-Host 'Owned backend writer stopped. Data services and volumes retained; restart explicitly after backup.'
