# Load jobra-backend/.env/local.env into current process environment (PowerShell).
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env\local.env"
if (-not (Test-Path $envFile)) {
    Write-Warning "Missing $envFile - copy .env\local.env.example to .env\local.env"
    exit 1
}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $i = $line.IndexOf("=")
    if ($i -lt 1) { return }
    $name = $line.Substring(0, $i).Trim()
    $val = $line.Substring($i + 1).Trim()
    [System.Environment]::SetEnvironmentVariable($name, $val, "Process")
}
Write-Host "Loaded environment from .env/local.env"
