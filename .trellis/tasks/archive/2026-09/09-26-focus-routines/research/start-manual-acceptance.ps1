param([int]$BackendPort = 18083, [int]$FrontendPort = 15174)

$ErrorActionPreference = 'Stop'
if (-not $env:POSTGRES_PASSWORD -or -not $env:MANUAL_FOCUS_PASSWORD) {
    throw 'Set POSTGRES_PASSWORD and MANUAL_FOCUS_PASSWORD for the isolated manual test.'
}
if ((Get-NetTCPConnection -State Listen -LocalPort $BackendPort -ErrorAction SilentlyContinue) -or
    (Get-NetTCPConnection -State Listen -LocalPort $FrontendPort -ErrorAction SilentlyContinue)) {
    throw 'Manual acceptance ports are occupied.'
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$jar = (Resolve-Path (Join-Path $root 'backend/target/backend-0.0.1-SNAPSHOT.jar')).Path
$vite = (Resolve-Path (Join-Path $root 'frontend/node_modules/vite/bin/vite.js')).Path
$java = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$node = (Get-Command node).Source
if (-not (Test-Path -LiteralPath $java)) { throw 'Java 17 executable is missing.' }

$env:DATABASE_URL = 'jdbc:postgresql://127.0.0.1:25432/focus_routines_test?currentSchema=d9_focus_manual_20260926'
$env:SPRING_FLYWAY_SCHEMAS = 'd9_focus_manual_20260926'
$env:SPRING_FLYWAY_DEFAULT_SCHEMA = 'd9_focus_manual_20260926'
$env:SPRING_FLYWAY_CREATE_SCHEMAS = 'true'
$env:POSTGRES_USER = 'focus_test'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '26379'
$env:FOCUS_WRITE_ENABLED = 'true'
$env:WORKBENCH_BOOTSTRAP_USERNAME = 'focus_manual_admin'
$env:WORKBENCH_BOOTSTRAP_PASSWORD = $env:MANUAL_FOCUS_PASSWORD
$env:WORKBENCH_WS_ALLOWED_ORIGINS = "http://127.0.0.1:$FrontendPort"
$env:VITE_API_TARGET = "http://127.0.0.1:$BackendPort"

$back = $null
$front = $null
$ready = $false
try {
    $back = Start-Process -FilePath $java -ArgumentList @('-jar', $jar, '--server.address=127.0.0.1', "--server.port=$BackendPort") `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $env:TEMP 'focus-manual-backend.log') `
        -RedirectStandardError (Join-Path $env:TEMP 'focus-manual-backend-error.log')
    $front = Start-Process -FilePath $node -ArgumentList @($vite, '--host', '127.0.0.1', '--port', "$FrontendPort", '--strictPort') `
        -WorkingDirectory (Join-Path $root 'frontend') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $env:TEMP 'focus-manual-frontend.log') `
        -RedirectStandardError (Join-Path $env:TEMP 'focus-manual-frontend-error.log')
    for ($i = 0; $i -lt 90; $i++) {
        if ($back.HasExited -or $front.HasExited) { throw 'Manual acceptance service exited; inspect focus-manual logs in TEMP.' }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$BackendPort/actuator/health" -TimeoutSec 2
            $web = Invoke-WebRequest "http://127.0.0.1:$FrontendPort" -UseBasicParsing -TimeoutSec 2
            if ($health.status -eq 'UP' -and $web.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Manual acceptance services did not become healthy.' }
    $runtime = [pscustomobject]@{
        backendPid = $back.Id; frontendPid = $front.Id; backendPath = $jar; frontendPath = $vite
        schema = 'd9_focus_manual_20260926'; url = "http://127.0.0.1:$FrontendPort"
    }
    $runtime | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $env:TEMP 'focus-manual-runtime.json')
    [pscustomobject]@{backendPid = $back.Id; frontendPid = $front.Id; health = 'UP'; frontendHttp = 200; url = $runtime.url }
} finally {
    if (-not $ready) {
        if ($front -and -not $front.HasExited) { Stop-Process -Id $front.Id -Force }
        if ($back -and -not $back.HasExited) { Stop-Process -Id $back.Id -Force }
    }
}
