param([ValidateSet('init','start','stop','status')][string]$Action = 'start', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
Push-Location $taskProject
try {
    if (-not (Test-Path -LiteralPath '.env')) {
        if ($Action -eq 'stop' -or $Action -eq 'status') { throw 'Ambiente ainda não inicializado. Execute scripts/dev.ps1 start.' }
        function New-LocalSecret { [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48)) }
        $taskEnv = "LOCAL_DB_PASSWORD=$(New-LocalSecret)`nLOCAL_JWT_SECRET=$(New-LocalSecret)`nLOCAL_API_PORT=18080`nLOCAL_DB_PORT=55432`nLOCAL_MAIL_PORT=8025`nLOCAL_CORS_ORIGINS=http://localhost:5173`nLOCAL_RESET_URL=http://localhost:5173/redefinir-senha`n"
        [IO.File]::WriteAllText((Join-Path $taskProject '.env'), $taskEnv, [Text.UTF8Encoding]::new($false))
        # Valores gerados exclusivamente para este Compose. Nunca imprimir o conteúdo.
        Write-Output 'Configuração local exclusiva criada em .env (ignorada pelo Git).'
    }
    if ($Action -eq 'init') { return }
    & docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker não está disponível. Inicie o Docker Desktop.' }
    switch ($Action) {
        'start' {
            if (-not $SkipBuild) {
                & .\mvnw.cmd '-Dmaven.test.skip=true' package
                if ($LASTEXITCODE -ne 0) { throw 'Build falhou.' }
            }
            & docker compose up -d --build --wait
            if ($LASTEXITCODE -ne 0) { throw 'Compose não iniciou. Verifique portas e Docker Desktop; não exiba docker compose config sem --quiet.' }
            $taskPublished = & docker compose port app 8080
            if ($LASTEXITCODE -ne 0) { throw 'Porta do aplicativo não encontrada.' }
            $taskHealthy = $false
            for ($taskAttempt=0; $taskAttempt -lt 45; $taskAttempt++) {
                try {
                    $taskHealth = Invoke-RestMethod -Uri ('http://' + $taskPublished.Trim() + '/actuator/health') -TimeoutSec 3
                    if ($taskHealth.status -eq 'UP') { $taskHealthy = $true; break }
                } catch { Start-Sleep -Seconds 1 }
            }
            if (-not $taskHealthy) { throw 'Containers iniciaram, mas health check não ficou UP.' }
            Write-Output 'Confere local iniciado. Padrão: http://localhost:18080/actuator/health e http://localhost:18080/swagger-ui/index.html; e-mails: http://localhost:8025.'
        }
        'stop' { & docker compose --profile tools down; if ($LASTEXITCODE -ne 0) { throw 'Falha ao parar ambiente.' } }
        'status' { & docker compose ps }
    }
} finally { Pop-Location }
