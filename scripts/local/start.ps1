. "$PSScriptRoot/common.ps1"
New-Item -ItemType Directory -Force -Path $Runtime | Out-Null
$envValues = Get-EnvValues
$java = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/java.exe")) { "$env:JAVA_HOME/bin/java.exe" } else { (Get-Command java).Source }
# Resolve Oracle's javapath launcher to the real JVM to keep the tracked PID stable.
$ErrorActionPreference = 'Continue'
$javaInfo = & $java -XshowSettings:properties -version 2>&1 | Out-String
$javaExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($javaExit -ne 0) { throw 'Java runtime check failed.' }
if ($javaInfo -match 'java.home\s*=\s*([^\r\n]+)') { $java = Join-Path $Matches[1].Trim() 'bin/java.exe' }
$jar = Join-Path $RepoRoot 'backend/target/backend-0.0.1-SNAPSHOT.jar'
$vite = Join-Path $RepoRoot 'frontend/node_modules/vite/bin/vite.js'
if (-not (Test-Path $jar) -or -not (Test-Path $vite)) { throw 'Build backend and run npm ci before starting (see README).' }
foreach ($service in @(@{name='backend';port=8080}, @{name='frontend';port=5173})) {
    $state = Join-Path $Runtime "$($service.name).json"
    if (Test-Path $state) {
        $entry = Get-Content -Raw -Encoding UTF8 $state | ConvertFrom-Json
        if (Get-OwnedProcess $entry) { Write-Host "$($service.name) already running (PID $($entry.pid))."; continue }
    }
    if (Get-NetTCPConnection -State Listen -LocalPort $service.port -ErrorAction SilentlyContinue) { throw "Port $($service.port) is occupied by an unmanaged process. Stop its original terminal first." }
    if ($service.name -eq 'backend') {
        $prior = @{}
        try {
            foreach ($key in $envValues.Keys) { $prior[$key] = [Environment]::GetEnvironmentVariable($key, 'Process'); [Environment]::SetEnvironmentVariable($key, $envValues[$key], 'Process') }
            $process = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$jar`"", '--spring.profiles.active=default', '--server.address=127.0.0.1', '--server.port=8080') -WorkingDirectory $RepoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$Runtime/backend.log" -RedirectStandardError "$Runtime/backend-error.log"
        } finally { foreach ($key in $prior.Keys) { [Environment]::SetEnvironmentVariable($key, $prior[$key], 'Process') } }
        Save-OwnedProcess $process.Id 'backend' $RepoRoot
    } else {
        $oldTarget = $env:VITE_API_TARGET
        $backendEnv = @{}
        try {
            foreach ($item in Get-ChildItem Env: | Where-Object { $_.Name -match '^(DEEPSEEK_|POSTGRES_|REDIS_|REPORT_AI_|DATABASE_URL$)' }) {
                $backendEnv[$item.Name] = $item.Value
                [Environment]::SetEnvironmentVariable($item.Name, $null, 'Process')
            }
            $env:VITE_API_TARGET = 'http://127.0.0.1:8080'
            $process = Start-Process -FilePath (Get-Command node).Source -ArgumentList @("`"$vite`"", '--host', '127.0.0.1', '--port', '5173', '--strictPort') -WorkingDirectory "$RepoRoot/frontend" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$Runtime/frontend.log" -RedirectStandardError "$Runtime/frontend-error.log"
        } finally {
            $env:VITE_API_TARGET = $oldTarget
            foreach ($key in $backendEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $backendEnv[$key], 'Process') }
        }
        Save-OwnedProcess $process.Id 'frontend' "$RepoRoot/frontend"
    }
}
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    foreach ($name in @('backend', 'frontend')) {
        $entry = Get-Content -Raw -Encoding UTF8 (Join-Path $Runtime "$name.json") | ConvertFrom-Json
        if (-not (Get-OwnedProcess $entry)) { throw "$name exited during startup. Inspect .local-runtime logs; run stop.ps1 before retrying." }
        $port = if ($name -eq 'backend') { 8080 } else { 5173 }
        $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
        if ($listener -and @($listener.OwningProcess | Where-Object { $_ -ne $entry.pid }).Count -gt 0) { throw "Port $port is not owned by the managed $name process." }
    }
    try { $health = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2; $null = Invoke-WebRequest 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 2; if ($health.status -eq 'UP') { $ready = $true; break } } catch { }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'Startup did not become healthy. Inspect .local-runtime logs, then run stop.ps1 before retrying.' }
Write-Host 'Ready: http://127.0.0.1:5173 (logs and PID ownership in .local-runtime)'
