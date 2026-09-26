param(
    [string]$Jar = 'backend/target/backend-0.0.1-SNAPSHOT.jar',
    [string]$Schema = 'd9_focus_rollout_20260926',
    [int]$Port = 18083
)

$ErrorActionPreference = 'Stop'
if (-not $env:POSTGRES_PASSWORD -or -not $env:POSTGRES_USER -or -not $env:REDIS_PORT -or -not $env:WORKBENCH_BOOTSTRAP_PASSWORD) {
    throw 'Set isolated POSTGRES_USER, POSTGRES_PASSWORD, REDIS_PORT and WORKBENCH_BOOTSTRAP_PASSWORD first.'
}
if ($Schema -notmatch '^d9_focus_rollout_[a-z0-9_]+$' -or $Port -eq 8080) {
    throw 'Use a dedicated rollout schema and non-application port.'
}
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$jarPath = (Resolve-Path (Join-Path $root $Jar)).Path
$ErrorActionPreference = 'Continue'
$javaInfo = & java -XshowSettings:properties -version 2>&1 | Out-String
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0 -or $javaInfo -notmatch 'java.home\s*=\s*([^\r\n]+)') { throw 'Cannot resolve the real Java runtime.' }
$javaExe = Join-Path $Matches[1].Trim() 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) { throw "Java runtime missing: $javaExe" }
$base = "http://127.0.0.1:$Port"
$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:25432/focus_routines_test?currentSchema=$Schema"
$env:SPRING_FLYWAY_SCHEMAS = $Schema
$env:SPRING_FLYWAY_DEFAULT_SCHEMA = $Schema
$env:SPRING_FLYWAY_CREATE_SCHEMAS = 'true'
$env:REDIS_HOST = '127.0.0.1'
$env:WORKBENCH_BOOTSTRAP_USERNAME = 'focus_rollout_admin'
$stdout = Join-Path $env:TEMP 'focus-rollout-stdout.log'
$stderr = Join-Path $env:TEMP 'focus-rollout-stderr.log'
$results = [ordered]@{ schema = $Schema; port = $Port; stages = @() }
$process = $null

function Start-App([bool]$enabled) {
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw "Port $Port is already occupied."
    }
    $env:FOCUS_WRITE_ENABLED = if ($enabled) { 'true' } else { 'false' }
    $args = @('-jar', $jarPath, "--server.port=$Port", '--server.address=127.0.0.1')
    $script:process = Start-Process -FilePath $javaExe -ArgumentList $args -WorkingDirectory $root `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    for ($i = 0; $i -lt 90; $i++) {
        if ($process.HasExited) { throw "Backend exited during startup; inspect $stderr" }
        try {
            $health = Invoke-RestMethod "$base/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw "Backend did not become healthy; inspect $stderr"
}

function Stop-App {
    if ($script:process -and -not $script:process.HasExited) {
        Stop-Process -Id $script:process.Id -Force
        $script:process.WaitForExit(10000) | Out-Null
    }
    $script:process = $null
    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)) { return }
        Start-Sleep -Milliseconds 250
    }
    throw "Port $Port stayed occupied after stopping owned backend."
}

function Login {
    $webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $token = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $webSession
    $body = @{ username = 'focus_rollout_admin'; password = $env:WORKBENCH_BOOTSTRAP_PASSWORD } | ConvertTo-Json
    Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $webSession `
        -ContentType 'application/json' -Headers @{ 'X-XSRF-TOKEN' = $token.token } -Body $body | Out-Null
    $token = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $webSession
    return @{ Session = $webSession; Headers = @{ 'X-XSRF-TOKEN' = $token.token } }
}

function Post-Json($path, $body, $auth) {
    return Invoke-RestMethod "$base$path" -Method Post -WebSession $auth.Session `
        -ContentType 'application/json' -Headers $auth.Headers -Body ($body | ConvertTo-Json -Depth 6)
}

function Expect-Conflict($path, $body, $auth) {
    try {
        Post-Json $path $body $auth | Out-Null
        throw "Expected HTTP 409 for $path"
    } catch [System.Net.WebException] {
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 409) { throw "Expected 409 for $path; received $status" }
        return $status
    }
}

try {
    Start-App $false
    $auth = Login
    $closed = Invoke-RestMethod "$base/api/focus/capabilities" -WebSession $auth.Session
    if ($closed.writeEnabled -ne $false) { throw 'Closed stage returned writeEnabled=true.' }
    $closedStart = Expect-Conflict '/api/focus/sessions' @{ requestId = [guid]::NewGuid().ToString(); title = 'closed stage'; targetMinutes = 1; intervalMinutes = 10 } $auth
    $results.stages += @{ name = 'closed-before-write'; capability = $closed.writeEnabled; startStatus = $closedStart }
    Stop-App

    Start-App $true
    $auth = Login
    $open = Invoke-RestMethod "$base/api/focus/capabilities" -WebSession $auth.Session
    if ($open.writeEnabled -ne $true) { throw 'Open stage returned writeEnabled=false.' }
    $started = Post-Json '/api/focus/sessions' @{ requestId = [guid]::NewGuid().ToString(); title = 'rollout drill'; targetMinutes = 1; intervalMinutes = 10 } $auth
    Start-Sleep -Seconds 2
    $ended = Post-Json "/api/focus/sessions/$($started.id)/end" @{ version = $started.version } $auth
    $today = Invoke-RestMethod "$base/api/focus/today" -WebSession $auth.Session
    if ($ended.phase -ne 'ENDED' -or $ended.focusMs -le 0 -or @($today.records | Where-Object { $_.sessionId -eq $started.id }).Count -ne 1) {
        throw 'Enabled stage did not settle exactly one focus record.'
    }
    $results.stages += @{ name = 'enabled-write'; capability = $open.writeEnabled; sessionId = $started.id; focusMs = $ended.focusMs; recordCount = 1 }
    Stop-App

    Start-App $false
    $auth = Login
    $closed = Invoke-RestMethod "$base/api/focus/capabilities" -WebSession $auth.Session
    $today = Invoke-RestMethod "$base/api/focus/today" -WebSession $auth.Session
    $retained = @($today.records | Where-Object { $_.sessionId -eq $started.id })
    $closedStart = Expect-Conflict '/api/focus/sessions' @{ requestId = [guid]::NewGuid().ToString(); title = 'closed again'; targetMinutes = 1; intervalMinutes = 10 } $auth
    if ($closed.writeEnabled -ne $false -or $retained.Count -ne 1 -or $retained[0].focusMs -ne $ended.focusMs) {
        throw 'Closed stage lost settled focus evidence.'
    }
    $results.stages += @{ name = 'closed-after-write'; capability = $closed.writeEnabled; startStatus = $closedStart; retainedRecordCount = $retained.Count; retainedFocusMs = $retained[0].focusMs }
    $results.result = 'PASS'
} finally {
    Stop-App
}

$results | ConvertTo-Json -Depth 6
