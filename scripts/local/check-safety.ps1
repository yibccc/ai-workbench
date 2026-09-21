# Read-only boundary checks: no service is started or stopped.
. "$PSScriptRoot/common.ps1"
$self = Get-CimInstance Win32_Process -Filter "ProcessId=$PID"
$entry = [pscustomobject]@{pid=$PID;created=$self.CreationDate.ToUniversalTime().ToString('o');command=$self.CommandLine;root=$RepoRoot}
if (-not (Get-OwnedProcess $entry)) { throw 'Matching identity was not recognized.' }
foreach ($property in @('created', 'command', 'root')) {
    $original = $entry.$property
    $entry.$property = 'mismatched'
    $rejected = $false
    try { $null = Get-OwnedProcess $entry } catch { $rejected = $true }
    $entry.$property = $original
    if (-not $rejected) { throw "Failed to reject changed $property." }
}
foreach ($script in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $tokens = $null; $errors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) { throw "PowerShell parse errors in $($script.Name)." }
}
Write-Host 'PASS: matching identity, changed creation time/command/root rejection, all script syntax.'
