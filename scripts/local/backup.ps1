param([string]$RestoreTo, [string]$ArchivePath)
. "$PSScriptRoot/common.ps1"
$values = Get-EnvValues
$database = if ($values.ContainsKey('POSTGRES_DB')) { $values.POSTGRES_DB } else { 'ai_workbench' }
$user = if ($values.ContainsKey('POSTGRES_USER')) { $values.POSTGRES_USER } else { 'ai_workbench' }
if ($values.ContainsKey('DATABASE_URL') -and $values.DATABASE_URL) { throw 'DATABASE_URL override requires a separately reviewed backup target; this script only backs up local Compose.' }
if ($database -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$' -or $user -notmatch '^[a-zA-Z_][a-zA-Z0-9_]*$') { throw 'Unsupported database/user identifier.' }
if ($RestoreTo -and ($RestoreTo -notmatch '^d10_restore_[a-z0-9_]{1,40}$' -or $RestoreTo -eq $database)) { throw 'Restore target must be a NEW database named d10_restore_<suffix>.' }
$container = 'ai-workbench-postgres-1'
$existing = @(Invoke-Docker @('exec', $container, 'psql', '-U', $user, '-d', 'postgres', '-At', '-c', 'SELECT datname FROM pg_database'))
if (-not $ArchivePath -and $existing -notcontains $database) { throw 'Configured source database was not found.' }
if ($RestoreTo -and $existing -contains $RestoreTo) { throw 'Restore database already exists; refusing to overwrite it.' }
$folder = Join-Path $RepoRoot '.local-backups/d10'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$suffix = [Guid]::NewGuid().ToString('N')
$archive = Join-Path $folder "$database-$suffix.dump"
if ($ArchivePath) {
    if (-not $RestoreTo) { throw 'ArchivePath requires RestoreTo (new isolated database).' }
    $archive = (Resolve-Path -LiteralPath $ArchivePath).Path
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) { throw 'ArchivePath must be a file.' }
}
$temp = "/tmp/workbench-$suffix.dump"
try {
    if (-not $ArchivePath) { Invoke-Docker @('exec', $container, 'pg_dump', '-U', $user, '-d', $database, '--format=custom', '--file', $temp) }
    $destination = $archive
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        $converted = & wsl.exe -d Ubuntu -- wslpath -a ($archive.Replace('\', '/'))
        if ($LASTEXITCODE -ne 0 -or -not $converted) { throw 'wslpath failed.' }
        $destination = $converted.Trim()
    }
    if ($ArchivePath) { Invoke-Docker @('cp', $destination, "${container}:$temp") }
    else { Invoke-Docker @('cp', "${container}:$temp", $destination) }
    Invoke-Docker @('exec', $container, 'pg_restore', '--list', $temp) | Out-Null
    Write-Host "Backup: $archive"
    Write-Host "SHA256: $((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash)"
    if ($RestoreTo) {
        Invoke-Docker @('exec', $container, 'createdb', '-U', $user, '-T', 'template0', $RestoreTo)
        Invoke-Docker @('exec', $container, 'pg_restore', '-U', $user, '-d', $RestoreTo, '--exit-on-error', '--no-owner', $temp)
        Write-Host "Restored into isolated database: $RestoreTo. Original $database unchanged."
    }
} finally {
    # Exact per-run file, never a directory or a wildcard.
    # Preserve a dump/restore failure if Docker also becomes unavailable during cleanup.
    try { Invoke-Docker @('exec', $container, 'rm', '-f', $temp) }
    catch { Write-Warning "Could not remove container temporary file ${temp}; remove this exact file when Docker is available." }
}
