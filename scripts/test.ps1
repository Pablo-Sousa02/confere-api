param([string]$PgBin = $env:PG_BIN, [switch]$UnitOnly)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
Push-Location $taskProject
try {
    if ($UnitOnly) {
        & .\mvnw.cmd test '-Dtest=EntidadesTests,ConciliacaoServiceTests,CredenciaisTests,LimiteAbusoTests'
        if ($LASTEXITCODE -ne 0) { throw 'Testes unitários falharam.' }
        return
    }
    if ($env:TEST_DB_URL) {
        if ($env:TEST_DB_URL -notmatch '^jdbc:postgresql://[^/]+/confere_test[a-zA-Z0-9_]*$' -or $env:TEST_DB_URL -eq $env:DB_URL) {
            throw 'TEST_DB_URL deve indicar um banco exclusivo confere_test..., diferente de DB_URL.'
        }
        & .\mvnw.cmd test
        if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
        return
    }
    if (-not $PgBin) {
        & .\mvnw.cmd test
        if ($LASTEXITCODE -ne 0) { throw 'Testes falharam. Verifique Docker Desktop.' }
        return
    }
    $taskInitdb = Join-Path $PgBin 'initdb.exe'
    $taskPgctl = Join-Path $PgBin 'pg_ctl.exe'
    $taskCreatedb = Join-Path $PgBin 'createdb.exe'
    foreach ($taskBinary in @($taskInitdb, $taskPgctl, $taskCreatedb)) {
        if (-not (Test-Path -LiteralPath $taskBinary)) { throw "Binário ausente: $taskBinary" }
    }
    $taskRoot = Join-Path $taskProject ('target\pg-test-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $taskRoot -Force | Out-Null
    $taskData = Join-Path $taskRoot 'data'
    $taskListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $taskListener.Start()
    $taskPort = $taskListener.LocalEndpoint.Port
    $taskListener.Stop()
    # Instância descartável, somente loopback. Não usa o serviço/banco de desenvolvimento.
    $taskInit = Start-Process -FilePath $taskInitdb -ArgumentList @('-D', ('"' + $taskData + '"'), '-U', 'postgres', '--auth=trust', '--encoding=UTF8', '--locale=C') -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $taskRoot 'init.out') -RedirectStandardError (Join-Path $taskRoot 'init.err')
    if ($taskInit.ExitCode -ne 0) { throw "initdb falhou. Consulte $taskRoot" }
    $taskStarted = $false
    try {
        $taskStart = Start-Process -FilePath $taskPgctl -ArgumentList @('-D', ('"' + $taskData + '"'), '-l', ('"' + (Join-Path $taskRoot 'postgres.log') + '"'), '-o', ('"-p ' + $taskPort + ' -h 127.0.0.1"'), '-w', 'start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskRoot 'start.out') -RedirectStandardError (Join-Path $taskRoot 'start.err')
        # WaitForExit aguarda somente pg_ctl; Start-Process -Wait aguarda também o servidor filho.
        $taskStart.WaitForExit()
        if ($taskStart.ExitCode -ne 0) { throw "PostgreSQL de teste não iniciou. Consulte $taskRoot" }
        $taskStarted = $true
        & $taskCreatedb -h 127.0.0.1 -p $taskPort -U postgres confere_test
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível criar o banco temporário.' }
        $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$taskPort/confere_test"
        $env:TEST_DB_USERNAME = 'postgres'
        $env:TEST_DB_PASSWORD = ''
        & .\mvnw.cmd test
        if ($LASTEXITCODE -ne 0) { throw 'Testes falharam.' }
    } finally {
        if ($taskStarted) {
            Start-Process -FilePath $taskPgctl -ArgumentList @('-D', ('"' + $taskData + '"'), '-m', 'fast', '-w', 'stop') -WindowStyle Hidden -Wait -RedirectStandardOutput (Join-Path $taskRoot 'stop.out') -RedirectStandardError (Join-Path $taskRoot 'stop.err') | Out-Null
        }
        Remove-Item Env:TEST_DB_URL -ErrorAction SilentlyContinue
        Remove-Item Env:TEST_DB_USERNAME -ErrorAction SilentlyContinue
        Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    }
} finally { Pop-Location }
