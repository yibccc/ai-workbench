# Lightweight capture verification; never starts the application or touches real DBs.
. (Join-Path $PSScriptRoot '../common.ps1')
$actualRepo = $RepoRoot
$folder = Join-Path $Runtime ('native-capture-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $folder | Out-Null
$Runtime = $folder

function Assert-True {
    param([bool]$Condition, [string]$Label)
    if (-not $Condition) { throw "Assertion failed: $Label" }
}

function Assert-Rejected {
    param([scriptblock]$Action, [string]$Label)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    Assert-True $rejected $Label
}

function With-BackendEnvironment {
    param([hashtable]$Values, [scriptblock]$Action)
    $previous = @{}
    try {
        foreach ($item in Get-ChildItem Env: | Where-Object { Test-BackendEnvironmentName $_.Name }) {
            $previous[$item.Name] = $item.Value
            [Environment]::SetEnvironmentVariable($item.Name, $null, 'Process')
        }
        foreach ($key in $Values.Keys) {
            if (-not $previous.ContainsKey($key)) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
            [Environment]::SetEnvironmentVariable($key, $Values[$key], 'Process')
        }
        & $Action
    } finally {
        foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    }
}

$child = $null
try {
    $defaultFile = Join-Path $folder '.env'
    $syntheticSecret = 'only-test-secret-never-state-or-frontend'
    $content = "POSTGRES_HOST=127.0.0.1`nPOSTGRES_PORT=25432`nPOSTGRES_DB=synthetic_native`nPOSTGRES_PASSWORD=$syntheticSecret`nDATABASE_URL=jdbc:postgresql://127.0.0.1:25432/synthetic_native?currentSchema=isolated`nWORKBENCH_STORAGE_SECRET_KEY=$syntheticSecret`nRUSTFS_SECRET_KEY=$syntheticSecret`n"
    Set-Content -LiteralPath $defaultFile -Value $content -Encoding UTF8
    # Exercise default and explicit resolution in a synthetic root. Never read user's .env.
    $RepoRoot = $folder
    $default = Get-EnvConfiguration
    $explicit = Get-EnvConfiguration -EnvFile $defaultFile
    Assert-True ($default.envFile -eq $explicit.envFile -and $default.envFileSha256 -eq $explicit.envFileSha256) 'default/explicit config identity'
    Assert-True ($default.envFile -eq (Resolve-Path -LiteralPath $defaultFile).Path) 'absolute env file'
    Assert-Rejected { Get-EnvConfiguration -EnvFile ' ' } 'blank explicit env rejected'
    $RepoRoot = $actualRepo

    $identity = Get-BackendDatabaseIdentity -Values $explicit.values
    Assert-True ($identity.database -eq 'synthetic_native' -and $identity.schema -eq 'isolated' -and $identity.port -eq 25432) 'DATABASE_URL before postgres defaults'
    $direct = Get-BackendDatabaseIdentity -Values @{ SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5433/direct_db?currentSchema=direct_schema'; DATABASE_URL = 'jdbc:postgresql://localhost/ignored' }
    Assert-True ($direct.database -eq 'direct_db' -and $direct.schema -eq 'direct_schema' -and $direct.port -eq 5433) 'direct Spring URL before application URL'
    $defaults = Get-BackendDatabaseIdentity -Values @{}
    Assert-True ($defaults.host -eq 'localhost' -and $defaults.port -eq 5432 -and $defaults.database -eq 'ai_workbench' -and $defaults.schema -eq 'public') 'application defaults'
    foreach ($url in @('', 'jdbc:postgresql://localhost/db?currentSchema=', 'jdbc:postgresql://localhost/db?currentSchema=a,b',
        'jdbc:postgresql://localhost/db?currentSchema=a&currentSchema=b', 'jdbc:postgresql://localhost/db?sslmode=require',
        'jdbc:postgresql://name:password@localhost/db', 'jdbc:postgresql://localhost/db#fragment')) {
        Assert-Rejected { Get-BackendDatabaseIdentity -Values @{ DATABASE_URL = $url } } 'unprovable URL rejected'
    }
    Assert-Rejected { Get-BackendDatabaseIdentity -Values @{ SPRING_APPLICATION_JSON = '{}' } } 'Spring JSON override rejected'
    Assert-Rejected { Get-BackendDatabaseIdentity -Values @{ JAVA_TOOL_OPTIONS = '-Dspring.datasource.url=secret' } } 'JVM property override rejected'
    $memoryOnly = Get-BackendDatabaseIdentity -Values @{ JAVA_TOOL_OPTIONS = '-Xmx128m' }
    Assert-True ($memoryOnly.database -eq 'ai_workbench') 'memory-only JVM options allowed'

    foreach ($name in @('POSTGRES_PASSWORD', 'DATABASE_URL', 'SPRING_DATASOURCE_PASSWORD', 'SPRING_DATASOURCE_URL', 'SPRING_APPLICATION_JSON',
        'SPRING_CONFIG_LOCATION', 'SPRING_FLYWAY_PASSWORD', 'TEST_DATABASE_URL', 'E2E_DATABASE_URL', 'LIVE_ACCEPTANCE_DATABASE_URL',
        'RUSTFS_SECRET_KEY', 'WORKBENCH_STORAGE_SECRET_KEY', 'DEEPSEEK_API_KEY', 'OPENAI_API_KEY', 'JAVA_TOOL_OPTIONS')) {
        Assert-True (Test-BackendEnvironmentName $name) 'backend variable removed from frontend'
    }
    Assert-True (-not (Test-BackendEnvironmentName 'VITE_API_TARGET')) 'frontend target remains public'

    $launch = With-BackendEnvironment $explicit.values { Get-BackendLaunchIdentity -Configuration $explicit -WorkingDirectory $actualRepo }
    Assert-True ($launch.service -eq 'backend' -and $launch.moduleDirectory -eq (Join-Path $actualRepo 'backend') -and
        $launch.workingDirectory -eq $actualRepo) 'backend module and actual cwd captured'
    Assert-True (-not (($launch | ConvertTo-Json -Depth 4).Contains($syntheticSecret))) 'capture contains no secrets'
    Add-Content -LiteralPath $defaultFile -Value '# changed' -Encoding UTF8
    $changedCapture = With-BackendEnvironment $explicit.values { Get-BackendLaunchIdentity -Configuration $explicit -WorkingDirectory $actualRepo }
    Assert-True ($null -eq $changedCapture.databaseIdentity -and $changedCapture.captureError -eq 'ENV_FILE_CHANGED_BEFORE_LAUNCH') 'env change produces unconfirmed capture'
    Set-Content -LiteralPath $defaultFile -Value $content -Encoding UTF8
    $explicit = Get-EnvConfiguration -EnvFile $defaultFile
    $launch = With-BackendEnvironment $explicit.values { Get-BackendLaunchIdentity -Configuration $explicit -WorkingDirectory $actualRepo }

    # Fake CIM proves ownership is checked without a process mutation.
    $fakeCreation = [DateTime]::UtcNow
    function Get-CimInstance {
        param([string]$ClassName, [string]$Filter, $ErrorAction)
        return [pscustomobject]@{ ProcessId = 65000; CreationDate = $fakeCreation; CommandLine = 'synthetic backend capture test' }
    }
    Save-OwnedProcess 65000 'backend' $actualRepo -LaunchIdentity $launch
    $stateFile = Join-Path $folder 'backend.json'
    $entry = Get-Content -Raw -LiteralPath $stateFile -Encoding UTF8 | ConvertFrom-Json
    Assert-True ($null -ne (Get-OwnedProcess $entry)) 'matching fake CIM accepted'
    foreach ($key in @('created', 'command', 'root')) {
        $old = $entry.$key
        $entry.$key = 'changed'
        Assert-Rejected { Get-OwnedProcess $entry } 'changed CIM identity rejected'
        $entry.$key = $old
    }
    Remove-Item Function:Get-CimInstance

    # Capture a new short owned process at launch, then ValidateOnly. This is not a JVM/app acceptance test.
    $child = With-BackendEnvironment $explicit.values {
        $actualLaunch = Get-BackendLaunchIdentity -Configuration $explicit -WorkingDirectory $actualRepo
        $actualProcess = Start-Process -FilePath (Get-Command powershell.exe).Source -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') -WorkingDirectory $actualRepo -WindowStyle Hidden -PassThru
        Save-OwnedProcess $actualProcess.Id 'backend' $actualRepo -LaunchIdentity $actualLaunch
        return $actualProcess
    }
    $originalState = Get-Content -Raw -LiteralPath $stateFile -Encoding UTF8
    $entry = $originalState | ConvertFrom-Json
    Assert-True (-not $originalState.Contains($syntheticSecret)) 'owned state has no credential values'
    $pause = Join-Path $PSScriptRoot '../pause-backup-writer.ps1'
    With-BackendEnvironment $explicit.values { & $pause -StateFile $stateFile -EnvFile $defaultFile -ValidateOnly }
    $child.Refresh()
    Assert-True (-not $child.HasExited -and (Test-Path -LiteralPath $stateFile)) 'ValidateOnly leaves real owned process and state'
    Add-Content -LiteralPath $defaultFile -Value '# later change' -Encoding UTF8
    Assert-Rejected { With-BackendEnvironment $explicit.values { & $pause -StateFile $stateFile -EnvFile $defaultFile -ValidateOnly } } 'changed env hash rejects pause'
    Set-Content -LiteralPath $defaultFile -Value $content -Encoding UTF8
    $entry.databaseIdentity.database = 'wrong_source'
    $entry | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Assert-Rejected { With-BackendEnvironment $explicit.values { & $pause -StateFile $stateFile -EnvFile $defaultFile -ValidateOnly } } 'wrong source DB rejects pause'
    $originalState | Set-Content -LiteralPath $stateFile -Encoding UTF8
    $entry = $originalState | ConvertFrom-Json
    $entry.PSObject.Properties.Remove('service')
    $entry | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Assert-Rejected { With-BackendEnvironment $explicit.values { & $pause -StateFile $stateFile -EnvFile $defaultFile -ValidateOnly } } 'unknown old state rejected'
    $child.Refresh()
    Assert-True (-not $child.HasExited) 'rejections leave owned process running'
    Stop-Process -InputObject $child -ErrorAction Stop
    $child = $null

    # Unprovable database config is captured honestly while ordinary launch continues.
    $unresolved = $explicit.values.Clone()
    $unresolved['SPRING_APPLICATION_JSON'] = '{"spring":{"datasource":{"url":"unprovable-test-value"}}}'
    $Runtime = $folder
    $child = With-BackendEnvironment $unresolved {
        $unconfirmedLaunch = Get-BackendLaunchIdentity -Configuration $explicit -WorkingDirectory $actualRepo
        Assert-True ($null -eq $unconfirmedLaunch.databaseIdentity -and $unconfirmedLaunch.captureError -eq 'DATABASE_IDENTITY_UNCONFIRMED') 'unprovable identity has safe capture error'
        $actualProcess = Start-Process -FilePath (Get-Command powershell.exe).Source -ArgumentList @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') -WorkingDirectory $actualRepo -WindowStyle Hidden -PassThru
        Save-OwnedProcess $actualProcess.Id 'backend' $actualRepo -LaunchIdentity $unconfirmedLaunch
        return $actualProcess
    }
    $unconfirmedState = Get-Content -Raw -LiteralPath $stateFile -Encoding UTF8
    Assert-True (-not $unconfirmedState.Contains('unprovable-test-value') -and -not $unconfirmedState.Contains($syntheticSecret)) 'capture failure exposes no config value'
    Assert-Rejected { With-BackendEnvironment $unresolved { & $pause -StateFile $stateFile -EnvFile $defaultFile -ValidateOnly } } 'unconfirmed identity cannot pause writer'
    $child.Refresh()
    Assert-True (-not $child.HasExited) 'capture failure allows launch and guards leave process running'
    Write-Host 'PASS: native launch capture defaults/overrides, env identity, secret exclusion, fake CIM, real owned process ValidateOnly and rejection guards.'
} finally {
    if (Test-Path Function:Get-CimInstance) { Remove-Item Function:Get-CimInstance }
    if ($child) { $child.Refresh(); if (-not $child.HasExited) { Stop-Process -InputObject $child -ErrorAction Stop } }
    # Only the exact per-test files are removed; no recursive/wildcard deletion.
    foreach ($file in @((Join-Path $folder 'backend.json'), (Join-Path $folder '.env'))) {
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    if (Test-Path -LiteralPath $folder) { Remove-Item -LiteralPath $folder }
}
