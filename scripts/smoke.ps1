param([string]$BaseUrl = 'http://localhost:18080', [string]$MailUrl = 'http://localhost:8025')
$ErrorActionPreference = 'Stop'
$taskHttp = [Net.Http.HttpClient]::new()
function Invoke-SmokeRequest([string]$Method, [string]$Path, [object]$Body, [string]$Token, [int]$Expected) {
    $taskRequest = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), ($BaseUrl + $Path))
    if ($Token) { $taskRequest.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
    if ($null -ne $Body) { $taskRequest.Content = [Net.Http.StringContent]::new(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json') }
    $taskResponse = $taskHttp.SendAsync($taskRequest).GetAwaiter().GetResult()
    if ([int]$taskResponse.StatusCode -ne $Expected) { throw "Falha de smoke: $Method $Path retornou $([int]$taskResponse.StatusCode)." }
    $taskText = $taskResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if ($taskText) { return $taskText | ConvertFrom-Json }
}
try {
    $taskReady = $false
    for ($taskAttempt=0; $taskAttempt -lt 45; $taskAttempt++) {
        try {
            $taskHealth = Invoke-SmokeRequest GET '/actuator/health' $null '' 200
            if ($taskHealth.status -eq 'UP') { $taskReady = $true; break }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $taskReady) { throw 'Aplicação não ficou saudável.' }
    $taskSpec = Invoke-SmokeRequest GET '/v3/api-docs' $null '' 200
    if ($taskSpec.components.securitySchemes.bearerAuth.scheme -ne 'bearer') { throw 'Swagger sem Bearer.' }
    $null = Invoke-SmokeRequest GET '/api/importacoes' $null '' 401
    $taskPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $taskEmail = [Guid]::NewGuid().ToString('N') + '@example.test'
    $taskRegistration = Invoke-SmokeRequest POST '/api/auth/cadastro' @{empresa='Smoke local';nome='Administrador de teste';email=$taskEmail;senha=$taskPassword} '' 201
    $taskToken = $taskRegistration.accessToken
    $null = Invoke-SmokeRequest POST '/api/auth/login' @{email=$taskEmail;senha=$taskPassword} '' 200
    $taskIds = @()
    foreach ($taskTipo in @('VENDAS','RECEBIMENTOS')) {
        $taskCsv = if ($taskTipo -eq 'VENDAS') { "referencia;data;descricao;valor`nA;2026-10-14;Venda;10`n" } else { "referencia;data;descricao;valor`nA;2026-10-14;Parcela 1;4`nA;2026-10-15;Parcela 2;6`n" }
        $taskForm = [Net.Http.MultipartFormDataContent]::new()
        $taskFile = [Net.Http.ByteArrayContent]::new([Text.Encoding]::UTF8.GetBytes($taskCsv))
        $taskFile.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('text/csv')
        $taskForm.Add($taskFile, 'arquivo', 'teste.csv')
        $taskRequest = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, ($BaseUrl + '/api/importacoes?tipo=' + $taskTipo))
        $taskRequest.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$taskToken)
        $taskRequest.Content = $taskForm
        $taskResponse = $taskHttp.SendAsync($taskRequest).GetAwaiter().GetResult()
        if ([int]$taskResponse.StatusCode -ne 200) { throw 'Importação de smoke falhou.' }
        $taskImported = $taskResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        $taskIds += $taskImported.id
    }
    $taskResults = Invoke-SmokeRequest POST ('/api/conciliacoes?importacaoVendasId='+$taskIds[0]+'&importacaoRecebimentosId='+$taskIds[1]) $null $taskToken 200
    if (@($taskResults)[0].status -ne 'CONCILIADO') { throw 'Pagamentos parciais falharam no smoke.' }
    $null = Invoke-SmokeRequest GET '/api/dashboard?historico=true' $null $taskToken 200
    $null = Invoke-SmokeRequest POST '/api/auth/recuperar-senha' @{email=$taskEmail} '' 202
    $taskSearch = Invoke-RestMethod -Uri ($MailUrl + '/api/v1/search?query=' + [Uri]::EscapeDataString('to:'+$taskEmail))
    if (-not $taskSearch.messages) { throw 'E-mail não capturado no Mailpit.' }
    $taskMail = Invoke-RestMethod -Uri ($MailUrl + '/api/v1/message/' + $taskSearch.messages[0].ID)
    $taskMatch = [regex]::Match($taskMail.Text, '#token=([A-Za-z0-9_-]{43})')
    if (-not $taskMatch.Success) { throw 'E-mail sem link de recuperação.' }
    $taskNewPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $null = Invoke-SmokeRequest POST '/api/auth/redefinir-senha' @{token=$taskMatch.Groups[1].Value;senha=$taskNewPassword} '' 204
    $null = Invoke-SmokeRequest GET '/api/importacoes' $null $taskToken 401
    $null = Invoke-SmokeRequest POST '/api/auth/login' @{email=$taskEmail;senha=$taskNewPassword} '' 200
    Write-Output 'Smoke aprovado: health, OpenAPI/Bearer, cadastro/login, CSV, pagamentos parciais, dashboard, SMTP e redefinição de senha. Nenhuma credencial foi exibida.'
} finally { $taskHttp.Dispose() }
