param([Parameter(Mandatory)][string]$Backup)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskBackup = (Resolve-Path -LiteralPath $Backup).Path
Push-Location $taskProject
try {
    # Destino fixo: outro contêiner sem porta publicada e sem volume do app.
    & docker compose --profile tools up -d --wait restore-db
    if ($LASTEXITCODE -ne 0) { throw 'Banco isolado de restauração não iniciou.' }
    $taskDatabase = 'confere_restore_' + [Guid]::NewGuid().ToString('N')
    & docker compose exec -T restore-db createdb -U confere_local $taskDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar banco exclusivo de restauração.' }
    & docker compose cp $taskBackup restore-db:/tmp/confere-restore.dump
    if ($LASTEXITCODE -ne 0) { throw 'Cópia do backup falhou.' }
    & docker compose exec -T restore-db pg_restore --exit-on-error --single-transaction --no-owner --no-acl -U confere_local -d $taskDatabase /tmp/confere-restore.dump
    if ($LASTEXITCODE -ne 0) { throw 'Restauração falhou.' }
    $taskQuery = "select (select count(*) from importacoes), (select count(*) from lancamentos), (select count(*) from usuarios), (select count(*) from conciliacoes), (select count(*) from resultados_conciliacao), (select count(*) from recuperacoes_senha), (select count(*) from flyway_schema_history where success)"
    $taskOriginal = & docker compose exec -T db psql -U confere_local -d confere_local -At -c $taskQuery
    if ($LASTEXITCODE -ne 0) { throw 'Verificação da origem falhou.' }
    $taskRestored = & docker compose exec -T restore-db psql -U confere_local -d $taskDatabase -At -c $taskQuery
    if ($LASTEXITCODE -ne 0 -or "$taskOriginal" -ne "$taskRestored") { throw 'Backup restaurado, mas contagens divergem. Faça o teste sem escritas concorrentes e com backup recente.' }
    Write-Output 'Restauração isolada concluída; contagens e histórico Flyway conferidos. Nenhum banco existente foi sobrescrito.'
} finally {
    & docker compose --profile tools stop restore-db *> $null
    Pop-Location
}
