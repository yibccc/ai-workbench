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

function Resolve-EnvFilePath {
    param([string]$EnvFile)
    if ($PSBoundParameters.ContainsKey('EnvFile') -and [string]::IsNullOrWhiteSpace($EnvFile)) { throw 'EnvFile must name a file.' }
    $path = if ($PSBoundParameters.ContainsKey('EnvFile')) { $EnvFile } else { Join-Path $RepoRoot '.env' }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        if ($PSBoundParameters.ContainsKey('EnvFile')) { throw "Environment file not found: $path" }
        throw 'Copy .env.example to .env and configure it first.'
    }
    return (Resolve-Path -LiteralPath $path).Path
}

function Get-EnvValues {
    param([string]$EnvFile)
    $values = @{}
    $path = if ($PSBoundParameters.ContainsKey('EnvFile')) { Resolve-EnvFilePath -EnvFile $EnvFile } else { Resolve-EnvFilePath }
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        if ($line -match '^\s*(?:#|$)') { continue }
        if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { throw 'Invalid .env line; use KEY=value.' }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
        $values[$Matches[1]] = $value
    }
    return $values
}

function Get-EnvConfiguration {
    param([string]$EnvFile)
    $path = if ($PSBoundParameters.ContainsKey('EnvFile')) { Resolve-EnvFilePath -EnvFile $EnvFile } else { Resolve-EnvFilePath }
    $before = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $values = Get-EnvValues -EnvFile $path
    $after = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $captureError = if ($before -ne $after) { 'ENV_FILE_CHANGED_DURING_READ' } else { $null }
    return @{ values = $values; envFile = $path; envFileSha256 = $before; captureError = $captureError }
}

function Get-BackendDatabaseIdentity {
    param([System.Collections.IDictionary]$Values)
    foreach ($key in @('SPRING_DATASOURCE_HIKARI_SCHEMA', 'SPRING_APPLICATION_JSON', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME')) {
        if ($Values[$key]) { throw 'Database capture cannot prove a Spring configuration override; use a direct datasource URL.' }
    }
    foreach ($key in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')) {
        if ($Values[$key] -match '(?i)-D(?:spring\.|DATABASE_URL(?:=|\s)|POSTGRES_(?:HOST|PORT|DB)(?:=|\s))') {
            throw 'Database capture cannot prove a JVM database configuration override; use a direct datasource URL.'
        }
    }
    $urlKey = if ($Values.Contains('SPRING_DATASOURCE_URL')) { 'SPRING_DATASOURCE_URL' } elseif ($Values.Contains('DATABASE_URL')) { 'DATABASE_URL' } else { $null }
    $schema = 'public'
    if ($urlKey) {
        $url = [string]$Values[$urlKey]
        $uri = $null
        if (-not $url.StartsWith('jdbc:postgresql://', [StringComparison]::Ordinal) -or
            -not [Uri]::TryCreate($url.Substring(5), [UriKind]::Absolute, [ref]$uri) -or
            -not $uri.Host -or $uri.UserInfo -or $uri.Fragment) {
            throw 'Database capture requires one explicit PostgreSQL URL without user info or fragment.'
        }
        $dbHost = $uri.Host.ToLowerInvariant()
        $dbPort = if ($uri.Port -eq -1) { 5432 } else { $uri.Port }
        $database = [Uri]::UnescapeDataString($uri.AbsolutePath.Substring(1))
        if ($uri.Query) {
            $options = $uri.Query.Substring(1).Split('&')
            if ($options.Count -ne 1) { throw 'Database capture accepts only one explicit currentSchema URL option.' }
            $pair = $options[0].Split('=', 2)
            if ($pair.Count -ne 2 -or [Uri]::UnescapeDataString($pair[0]) -cne 'currentSchema') {
                throw 'Database capture accepts only one explicit currentSchema URL option.'
            }
            $schema = [Uri]::UnescapeDataString($pair[1].Replace('+', ' '))
        }
    } else {
        $dbHost = if ($Values.Contains('POSTGRES_HOST')) { [string]$Values['POSTGRES_HOST'] } else { 'localhost' }
        $dbPort = if ($Values.Contains('POSTGRES_PORT')) { [string]$Values['POSTGRES_PORT'] } else { '5432' }
        $database = if ($Values.Contains('POSTGRES_DB')) { [string]$Values['POSTGRES_DB'] } else { 'ai_workbench' }
    }
    $parsedPort = 0
    if ($dbHost -notmatch '^[a-zA-Z0-9][a-zA-Z0-9.-]*$' -or
        -not [int]::TryParse([string]$dbPort, [ref]$parsedPort) -or $parsedPort -lt 1 -or $parsedPort -gt 65535 -or
        $database -cnotmatch '^[a-z_][a-z0-9_]{0,62}$' -or $schema -cnotmatch '^[a-z_][a-z0-9_]{0,62}$') {
        throw 'Database capture requires an explicit single host, valid port, database identifier and single schema identifier.'
    }
    return @{ host = $dbHost.ToLowerInvariant(); port = $parsedPort; database = $database; schema = $schema }
}

function Get-BackendLaunchIdentity {
    param([hashtable]$Configuration, [string]$WorkingDirectory)
    $effective = [Environment]::GetEnvironmentVariables('Process')
    $identity = $null
    $captureError = $Configuration.captureError
    if (-not $captureError) {
        try { $identity = Get-BackendDatabaseIdentity -Values $effective }
        catch { $captureError = 'DATABASE_IDENTITY_UNCONFIRMED' }
    }
    try {
        if ((Get-FileHash -LiteralPath $Configuration.envFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Configuration.envFileSha256) {
            $identity = $null
            $captureError = 'ENV_FILE_CHANGED_BEFORE_LAUNCH'
        }
    } catch {
        $identity = $null
        $captureError = 'ENV_FILE_UNAVAILABLE_BEFORE_LAUNCH'
    }
    return @{ service = 'backend'; moduleDirectory = (Join-Path $RepoRoot 'backend'); workingDirectory = $WorkingDirectory;
        envFile = $Configuration.envFile; envFileSha256 = $Configuration.envFileSha256; databaseIdentity = $identity; captureError = $captureError }
}

function Test-BackendEnvironmentName {
    param([string]$Name)
    return $Name -match '^(DEEPSEEK_|OPENAI_|ANTHROPIC_|GEMINI_|POSTGRES_|REDIS_|REPORT_AI_|WORKBENCH_|RUSTFS_|AWS_|SPRING_|DATABASE_URL$|TEST_DATABASE_URL$|E2E_DATABASE_URL$|LIVE_ACCEPTANCE_DATABASE_URL$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)'
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
    param([int]$ProcessId, [string]$Name, [string]$Directory, [hashtable]$LaunchIdentity)
    if ($Name -eq 'backend' -and (-not $LaunchIdentity -or $LaunchIdentity.service -ne 'backend' -or
        $LaunchIdentity.moduleDirectory -ne (Join-Path $RepoRoot 'backend') -or $LaunchIdentity.workingDirectory -ne $Directory)) {
        throw 'New backend state requires its actual launch identity.'
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
    if (-not $process) { throw "$Name exited before its identity could be recorded." }
    $entry = @{ pid = $ProcessId; created = $process.CreationDate.ToUniversalTime().ToString('o'); command = $process.CommandLine; root = $RepoRoot; directory = $Directory }
    if ($Name -eq 'backend') {
        foreach ($key in @('service', 'moduleDirectory', 'workingDirectory', 'envFile', 'envFileSha256', 'databaseIdentity', 'captureError')) { $entry[$key] = $LaunchIdentity[$key] }
    }
    $entry | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $Runtime "$Name.json") -Encoding UTF8
}
