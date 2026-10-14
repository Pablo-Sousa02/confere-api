# Preparação para implantação (não executada)

O Compose da raiz é exclusivamente local: portas em loopback, e-mail capturado pelo Mailpit e segredos gerados para esse ambiente. Não o exponha diretamente à internet nem reutilize suas credenciais em produção.

O caminho Render está no README e em `render.yaml`: demonstração Free, PostgreSQL privado, TLS obrigatório e health check `/actuator/health/readiness`. A importação/confirmação do Blueprint depende de sua conta; nada foi publicado. O perfil `prod,render` usa o proxy HTTPS gerenciado pelo Render e não requer keystore. O Dockerfile agora constrói o JAR via Maven Wrapper em um estágio separado, sem receber `.env`/segredos. As migrations existentes não foram alteradas.

## HTTPS

`deploy/Caddyfile.example` prepara terminação TLS via proxy. Antes de usar, são necessários um domínio controlado com DNS apontando ao servidor, portas 80/443 acessíveis e um volume persistente para certificados do Caddy. Configure `CONFERE_DOMAIN`, conecte proxy e aplicativo em uma rede privada e publique apenas as portas do proxy. Nenhum domínio ou certificado público foi criado nesta tarefa. Não há necessidade de certificados para o desenvolvimento de loopback.

Alternativamente, o perfil Spring `prod` aceita TLS direto: `TLS_ENABLED=true`, `TLS_KEY_STORE` apontando para PKCS12 montado somente para leitura e `TLS_KEY_STORE_PASSWORD` fornecida por secret manager. Com proxy, mantenha `TLS_ENABLED=false` e não publique a porta do aplicativo. O aplicativo usa `server.forward-headers-strategy=none`: headers enviados pelo cliente não mudam o IP usado no rate limiting. Atrás de proxy, o limite por IP se aplica ao proxy; configure uma política de IP real com proxies explicitamente confiáveis antes de mudar isso, e aplique limites também na borda.

## E-mail e identidade

Perfil `prod`: sem configuração de e-mail, o aplicativo inicia com recuperação desabilitada; a prontidão não depende de SMTP. Para habilitar, defina `PASSWORD_RESET_ENABLED=true`, `RESET_PASSWORD_URL` HTTPS do futuro frontend, `EMAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, além das variáveis de banco/JWT. SMTP autenticado exige STARTTLS. O domínio remetente requer SPF/DKIM/DMARC no provedor de e-mail. No Render Free, as portas SMTP usuais são bloqueadas; consulte os limites atuais antes de escolher o transporte/provedor. Não existe dependência de provedor específico. O fluxo local usa Mailpit e não envia e-mails reais.

O token do link de recuperação fica no fragmento `#token=...`, para não aparecer em access logs HTTP. O frontend deve ler esse fragmento, removê-lo da barra de endereços e enviar token/senha por POST em HTTPS; não instalar analytics nessa rota. Esta tarefa não cria o frontend. Tokens não são devolvidos pelo endpoint de solicitação nem armazenados em texto puro. A resposta da solicitação é genérica, inclusive em falha SMTP; monitore a saúde SMTP e confirme a entrega antes da exposição externa. O envio atual é síncrono com timeouts de três segundos, sem fila durável/retry automático.

## Backups

`scripts/backup.ps1` usa `pg_dump -Fc --no-owner --no-acl` no **serviço db do Compose**, copia o arquivo sem pipeline binário do PowerShell e salva em `.local/backups` (ignorado pelo Git). `scripts/restore-test.ps1 -Backup <arquivo>` restaura em banco novo `confere_restore_<id>` no serviço isolado `restore-db`, em tmpfs, sem portas publicadas, e valida contagens das tabelas e histórico Flyway contra a origem. Não aceita destino arbitrário nem sobrescreve uma base existente. Execute essa comparação com backup recente e sem escritas concorrentes. A instância de restauração é parada em `finally`.

Para uma base real, um operador precisa adaptar o transporte a seu ambiente, usando `PGPASSFILE`/secret manager, PostgreSQL compatível, destino separado e acesso mínimo; não coloque senhas na linha de comando. Programe retenção, criptografia, cópias fora da máquina e restaurações periódicas com validação funcional. O teste local de contagens não substitui ensaio de recuperação completo, verificação de integridade de todos os dados e definição de RPO/RTO.

## Limitações operacionais

Rate limiting usa memória limitada por instância, sem serviço externo; reiniciar zera contadores. Múltiplas réplicas exigem proteção distribuída na borda/armazenamento compartilhado. Não há MFA, verificação de e-mail, refresh tokens ou auditoria completa de alteração de usuários. Controle cadastro público, permissões SQL diretas, retenção/LGPD, recursos dos containers e rotação de segredos. Tokens de recuperação expirados permanecem como hashes até uma política operacional de retenção ser definida. O estado está preparado para integração local, não constitui homologação de produção.
