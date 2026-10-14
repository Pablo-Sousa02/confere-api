param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
Push-Location $taskProject
try {
    if (-not $OutputDirectory) { $OutputDirectory = Join-Path $taskProject '.local/backups' }
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $taskBackup = Join-Path (Resolve-Path -LiteralPath $OutputDirectory) ('confere-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8) + '.dump')
    & docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl -f /tmp/confere-backup.dump'
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou.' }
    & docker compose cp db:/tmp/confere-backup.dump $taskBackup *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Cópia do backup falhou.' }
    Write-Output $taskBackup
} finally { Pop-Location }
