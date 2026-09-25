Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Runtime = Join-Path $RepoRoot '.local-runtime'

function Invoke-Docker {
    param([string[]]$DockerArgs)
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker @DockerArgs
    } else {
        & wsl.exe -d Ubuntu -- docker @DockerArgs
    }
    if ($LASTEXITCODE -ne 0) { throw "Docker command failed (exit $LASTEXITCODE)." }
}

function Get-EnvValues {
    param([string]$EnvFile)
    $values = @{}
    if ($PSBoundParameters.ContainsKey('EnvFile') -and [string]::IsNullOrWhiteSpace($EnvFile)) { throw 'EnvFile must name a file.' }
    $path = if ($PSBoundParameters.ContainsKey('EnvFile')) { $EnvFile } else { Join-Path $RepoRoot '.env' }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        if ($PSBoundParameters.ContainsKey('EnvFile')) { throw "Environment file not found: $path" }
        throw 'Copy .env.example to .env and configure it first.'
    }
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        if ($line -match '^\s*(?:#|$)') { continue }
        if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw 'Invalid .env line; use KEY=value.' }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
        $values[$Matches[1]] = $value
    }
    return $values
}

function Get-OwnedProcess {
    param($Entry)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($Entry.pid)" -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.CreationDate.ToUniversalTime().ToString('o') -ne $Entry.created -or $process.CommandLine -ne $Entry.command -or $Entry.root -ne $RepoRoot) {
        throw "PID $($Entry.pid) identity changed; refusing to stop/reuse it."
    }
    return $process
}

function Save-OwnedProcess {
    param([int]$ProcessId, [string]$Name, [string]$Directory)
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
    if (-not $process) { throw "$Name exited before its identity could be recorded." }
    @{ pid = $ProcessId; created = $process.CreationDate.ToUniversalTime().ToString('o'); command = $process.CommandLine; root = $RepoRoot; directory = $Directory } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Runtime "$Name.json") -Encoding UTF8
}
