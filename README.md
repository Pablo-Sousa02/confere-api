# Confere

Backend para conferir vendas e recebimentos importados de CSV, identificar diferenças e consultar o histórico de conciliações. Os dados são separados por empresa; o frontend React será desenvolvido em outra etapa. Não há IA generativa.

## Render: Blueprint pronto para demonstração

O [`render.yaml`](render.yaml) cria **um serviço web Docker e um PostgreSQL, ambos `plan: free`**, na mesma região, conectados pela rede privada com TLS (`sslmode=require`). O acesso externo ao banco fica bloqueado (`ipAllowList: []`). Não há plano pago, disco adicional, autoscaling ou upgrade automático definido. A criação só ocorre quando você confirmar no Render; preparar estes arquivos não cria serviços.

Passos que exigem sua conta/decisão:

1. Revise `git status` e os arquivos pendentes — inclusive os arquivos da implementação multiempresa que ainda não estão versionados. Confira que `.env`, `.local/`, dumps e certificados estão ignorados. Faça você o commit/push dos fontes, migrations V1–V3, Maven Wrapper, Dockerfile, `.dockerignore` e `render.yaml` no repositório GitHub desejado. Nenhum commit/push foi feito nesta tarefa.
2. No Render, conecte sua conta GitHub autorizando esse repositório. Escolha **New → Blueprint**, repositório/branch e caminho `render.yaml` na raiz. Não configure Root Directory: `pom.xml` e Dockerfile estão na raiz real do projeto.
3. Antes de confirmar, confira que **os dois recursos estão no plano Free**, a região é a mesma e os nomes `confere-demo`/`confere-demo-db` não coincidem com serviços existentes que você queira preservar. Se a conta já usou a cota gratuita ou o Free não estiver disponível, **pare sem criar/atualizar serviços**; qualquer plano pago depende de sua decisão. Revise também as cotas de uso/builds no painel. Não há substituição automática por plano pago no YAML.
4. Confirme a criação somente se aceitar os limites. Render construirá a imagem a partir dos fontes usando Java 21 e o Maven Wrapper. O Blueprint injeta a URL privada do banco e gera `JWT_SECRET` em Base64 com 256 bits, aceito pelo HS256 atual; não é preciso digitar segredos. Flyway aplica as migrations na base nova do Render, e Hibernate apenas valida o esquema. **Não há cópia/migração dos dados do PostgreSQL local.**
5. Após o primeiro deploy, abra `https://<endereço-exibido-pelo-Render>/actuator/health/readiness`. Deve responder 200 e `{"status":"UP"}`; a prontidão verifica o banco e devolve 503 se não houver conexão. Em **serviço web → Logs**, consulte inicialização/Flyway e falhas de build em **Events → deploy → logs**. Não publique dumps de ambiente, `DATABASE_URL`, respostas de login ou cabeçalhos Bearer ao compartilhar logs. Novas publicações são manuais no painel (**Manual Deploy → Deploy latest commit**), pois o Blueprint usa `autoDeployTrigger: "off"`.

**Limites do Free, conferidos na [documentação oficial](https://render.com/docs/free):** web suspende após 15 minutos sem tráfego, tem filesystem efêmero e pode sofrer cold start; PostgreSQL gratuito tem 1 GB, no máximo uma instância ativa por workspace e **expira após 30 dias**, com perda posterior dos dados se não houver decisão de upgrade/exportação. Não há backups gerenciados no PostgreSQL Free. Há cotas de horas, builds e tráfego; verifique elegibilidade/limites da conta no painel antes de confirmar. A memória do serviço gratuito é restrita e este backend Java pode iniciar lentamente; isso não justifica contratar outro plano automaticamente. Use somente dados fictícios para demonstração.

Configuração inicial segura, sem frontend: CORS fica vazio, a API continua aceitando chamadas de clientes HTTP sem `Origin` (por exemplo curl/Postman), e cadastro/login estão disponíveis nos endpoints documentados abaixo. Swagger fica desligado no perfil `prod`: `/v3/api-docs` e `/swagger-ui/index.html` retornam 404. Para teste temporário, altere `API_DOCS_ENABLED=true` no painel e faça deploy manual; volte a desativá-lo depois. Quando houver frontend, configure `CORS_ALLOWED_ORIGINS` com sua origem HTTPS exata, sem `*`. O servidor escuta `0.0.0.0:${PORT}`, com 8080 como padrão local. O HTTPS público é terminado pelo Render no domínio que ele fornecer; domínio próprio é opcional e não foi provisionado.

**Recuperação de senha opcional:** `PASSWORD_RESET_ENABLED=false` no Blueprint. Sem SMTP e URL do frontend, o backend inicia normalmente; a solicitação mantém a resposta genérica 202, mas **não envia e-mail nem cria token**. Para habilitar, configure `PASSWORD_RESET_ENABLED=true`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `EMAIL_FROM` e `RESET_PASSWORD_URL` HTTPS no painel. O Free bloqueia saída nas portas SMTP 25/465/587: habilite somente com transporte/porta permitida pelo plano e pelo provedor. A implementação atual usa SMTP com STARTTLS, não uma API de e-mail HTTP. Não há serviço de e-mail pago escolhido por estes arquivos.

`DATABASE_URL` aceita `postgres://` ou `postgresql://` com usuário/senha na URL e separa-os das propriedades JDBC para não registrá-los junto à URL. A conversão mantém caracteres percent-encoded nas credenciais e exige TLS por padrão; apenas `sslmode` é aceito como parâmetro. O Render privado usa certificado próprio e `require`; `verify-ca`/`verify-full` dependem de certificado compatível. Conexão sem TLS só é aceita para loopback em testes. A configuração local JDBC explícita por `DB_URL` continua prioritária. Não imprima a URL original para diagnóstico. [Referência de conexão](https://render.com/docs/postgresql-creating-connecting).

Antes de dados reais, continuam pendentes: backups externos com retenção/restauração homologada, proteção distribuída contra abuso e política de proxies confiáveis, entrega durável de e-mails, MFA/verificação de e-mail e validação financeira das heurísticas de duplicidade/sobreposição. Rate limiting continua por instância e, atrás do proxy, pode agrupar clientes por IP de conexão; não habilitamos confiança livre em `X-Forwarded-For`. Os scripts de backup atuais são para o Compose local, **não** para o Render; o banco público do Blueprint está bloqueado e não há exportação externa automática. As regras de pagamentos parciais e os limites do dashboard não foram alterados para hospedar. Consulte [implantação](docs/implantacao.md). Isto prepara uma demonstração, não produção financeira.

Validação local, sem conta Render:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
docker build -t confere-render-check:local .
git diff --check
```

O build multi-stage não precisa de `target/` no Git nem do `.env`; só copia `pom.xml`, Maven Wrapper e `src/main`. O runtime usa Java 21 JRE Alpine como UID 10001. Os testes usam bancos descartáveis, nunca o banco local existente. O esquema oficial para validar o Blueprint está em `https://render.com/schema/render.yaml.json`; a validação local de estrutura não substitui a checagem de elegibilidade/cotas feita pelo Render ao importar.

## Iniciar localmente sem preencher variáveis

Pré-requisitos desse caminho: JDK 21, PowerShell 7 e Docker Desktop iniciado (Docker Compose incluído).

```powershell
.\scripts\dev.ps1 start
```

O comando gera `.env` **local e ignorado pelo Git**, com segredos aleatórios exclusivos, compila o JAR e inicia Confere, PostgreSQL e Mailpit no projeto Docker `confere-local`. Não lê credenciais da IDE, não usa a porta 5432 do host e não acessa o banco PostgreSQL existente. O volume `confere-local_postgres_data` preserva os dados entre reinicializações. Não apague/recrie `.env` mantendo esse volume: os segredos gerados pertencem a ele. Para mudar as portas/origem local, ajuste somente as chaves `LOCAL_*` não secretas desse arquivo. Não exiba seu conteúdo ou execute `docker compose config` sem `--quiet`.

| Recurso local | Endereço padrão |
| --- | --- |
| Health check público, sem detalhes | `http://localhost:18080/actuator/health` |
| Swagger | `http://localhost:18080/swagger-ui/index.html` |
| OpenAPI | `http://localhost:18080/v3/api-docs` |
| Captura de e-mails Mailpit | `http://localhost:8025` |
| PostgreSQL exclusivo do Compose | `127.0.0.1:55432` |
| Origem permitida do futuro React | `http://localhost:5173` |

Todas as portas publicadas estão em loopback. SMTP não é publicado; o app acessa `mailpit:1025` pela rede Docker. Não há usuário do aplicativo ou senha padrão: use o cadastro no Swagger. Nenhum frontend foi construído.

```powershell
.\scripts\dev.ps1 status
.\scripts\smoke.ps1
.\scripts\dev.ps1 stop
```

`stop` encerra/remove os containers do projeto, preservando o volume. O smoke cria uma empresa e dados de teste no **Compose**, testa fluxos e e-mail real via SMTP, sem imprimir tokens/senhas. `dev.ps1 start -SkipBuild` reutiliza o JAR existente após uma validação/build recente. Os dados de smoke permanecem nesse ambiente para diagnóstico. O caminho manual abaixo é alternativo para ambientes fora do Compose; nele as variáveis precisam ser provisionadas pelo operador.

## Tecnologias e pré-requisitos

Java 21, Spring Boot **4.1.1**, Spring MVC, Spring Data JPA, Spring Security Resource Server, JWT HS256, BCrypt, PostgreSQL, Flyway, Commons CSV e Swagger/OpenAPI (springdoc). Maven Wrapper incluído. PostgreSQL 18 foi usado na validação local. Instale JDK 21 e PostgreSQL; não é necessário instalar Maven globalmente. O primeiro build requer acesso ao Maven Central.

## Configuração e execução

Crie um banco exclusivo para a aplicação e um usuário de banco com permissão de criar/alterar tabelas, sequências e índices nesse banco. Use o cliente PostgreSQL e um gerenciador de segredos para configurar a senha, sem incluí-la em scripts versionados. Faça backup da base existente antes de aplicar migrations em um ambiente real.

Defina as variáveis **no ambiente do processo**, usando o gerenciador de segredos ou a configuração local da IDE. Não versione arquivos `.env`, senhas ou tokens. Não use credenciais na linha de comando do Java/Maven (elas podem aparecer no histórico e na lista de processos).

| Variável | Uso |
| --- | --- |
| `DB_URL` | JDBC da base da aplicação, por exemplo `jdbc:postgresql://localhost:5432/confere_db` |
| `DB_USERNAME` | Usuário PostgreSQL da aplicação |
| `DB_PASSWORD` | Senha desse usuário |
| `JWT_SECRET` | Pelo menos 32 bytes criptograficamente aleatórios, codificados em Base64; mantenha estável entre reinicializações e instâncias |
| `CORS_ALLOWED_ORIGINS` | Origens exatas separadas por vírgula, por exemplo `http://localhost:5173`; vazio por padrão, sem liberação CORS |
| `BOOTSTRAP_ENABLED` | `false` por padrão; habilite somente para provisionar o administrador dos dados anteriores |
| `BOOTSTRAP_ADMIN_NAME` | Nome do administrador inicial, apenas no bootstrap |
| `BOOTSTRAP_ADMIN_EMAIL` | E-mail do administrador inicial, apenas no bootstrap |
| `BOOTSTRAP_ADMIN_PASSWORD` | Senha desse administrador, apenas no bootstrap |

Gere `JWT_SECRET` em um gerenciador de segredos com gerador criptográfico e armazene-o ali. Não há segredo nem senha padrão. A aplicação recusa chave JWT inválida/curta e CORS com curingas.

No PowerShell, na raiz:

```powershell
.\mvnw.cmd spring-boot:run
```

Em Linux/macOS use `./mvnw spring-boot:run`. Alternativa de empacotamento: `./mvnw package -DskipTests`, seguido de `java -jar target/confere-0.0.1-SNAPSHOT.jar`, com as mesmas variáveis no ambiente. `-DskipTests` apenas empacota; não constitui validação.

O servidor inicia em `http://localhost:8080`. Flyway executa as migrations e Hibernate valida o esquema; não usamos `ddl-auto=update`. SQL e corpos de requisições não são registrados pela aplicação. Não habilite logs de payload, parâmetros JDBC, headers de autenticação ou respostas de login na infraestrutura.

## Base existente, empresa inicial e bootstrap

`V1__create_import_tables.sql` permanece intacta. A nova `V2__empresas_usuarios_historico.sql` cria empresas, usuários e histórico; adiciona `empresa_id` às importações e associa **todos os registros anteriores** à empresa interna marcada `inicial=true`. Lançamentos e erros mantêm seus vínculos por importação e não são apagados. Chaves estrangeiras compostas impedem histórico com importações de empresas diferentes.

Para uma base que já aplicou a V1, inicie a nova versão normalmente: Flyway aplica somente a V2. Para base vazia, Flyway aplica V1 e V2, incluindo a empresa inicial vazia. Não edite checksums nem habilite baseline automaticamente em uma base preexistente sem histórico Flyway.

Para administrar os dados anteriores:

1. O operador autorizado do servidor define `BOOTSTRAP_ENABLED=true` e os três campos `BOOTSTRAP_ADMIN_*` em ambiente seguro.
2. Inicia a aplicação. Após a migration, a rotina bloqueia a empresa inicial e cria **um único administrador**, somente se ela ainda não tem usuários. Usa BCrypt e a mesma validação de senha/e-mail do cadastro. E-mail já usado por outra empresa impede o provisionamento.
3. Remove as variáveis `BOOTSTRAP_ADMIN_*`, desabilita `BOOTSTRAP_ENABLED` e reinicia. Faça login com o administrador criado.

A rotina é idempotente e não redefine credenciais. Não existe endpoint para reivindicar empresas por ID; o cadastro público sempre cria outra empresa. Recuperação posterior de uma conta/empresa exige um procedimento operacional ainda a definir. Controle rigorosamente quem pode alterar as variáveis de bootstrap. O nome inicial é descritivo; a renomeação dessa empresa ainda não tem endpoint.

## Autenticação, usuários e autorização

Cadastro público de **nova** empresa e seu primeiro administrador:

```http
POST /api/auth/cadastro
Content-Type: application/json

{"empresa":"Minha empresa","nome":"Nome do administrador","email":"admin@example.com","senha":"<senha definida pelo usuário>"}
```

Login:

```http
POST /api/auth/login
Content-Type: application/json

{"email":"admin@example.com","senha":"<senha definida pelo usuário>"}
```

Ambos retornam `{ "accessToken": "<JWT>", "tokenType": "Bearer", "expiresAt": "<instante UTC>" }`. Cadastro retorna 201. Senhas exigem no mínimo 12 caracteres e no máximo **72 bytes UTF-8**, limite do BCrypt; nunca são devolvidas. E-mails são normalizados com remoção de espaços nas pontas e conversão para minúsculas e são únicos em toda a base. Essa unicidade global permite login apenas com e-mail e senha.

Nos demais endpoints:

```http
Authorization: Bearer <accessToken>
```

JWT expira em 30 minutos, tem emissor e audiência verificados e inclui usuário, empresa e papel. A identidade é revalidada no banco a cada requisição; alterar papel, senha ou situação do usuário invalida seus tokens anteriores. A API é stateless, não usa sessão/cookie de autenticação. Não há refresh token ou logout remoto individual; descarte o token no cliente e faça novo login ao expirar.

`ADMIN` gerencia usuários **da própria empresa**. `ADMIN` e `ANALISTA` podem importar, consultar erros, conciliar e consultar dashboard/histórico. Gestão de usuários:

| Método e rota | Entrada / resposta |
| --- | --- |
| `GET /api/usuarios?page=0&size=20` | Página de `id,nome,email,papel,ativo` |
| `POST /api/usuarios` | JSON `nome,email,senha,papel` (`ADMIN` ou `ANALISTA`); retorna resumo, 201 |
| `PUT /api/usuarios/{id}` | JSON `nome,papel,ativo` e opcional `senha`; retorna resumo |

Não se remove o último administrador ativo. Inativar substitui a exclusão para preservar auditoria. E-mail não é alterável nessa versão. O servidor não aceita `empresaId` nos DTOs; empresa/papel da identidade nunca vêm de headers personalizados ou query params. IDs de outra empresa retornam o mesmo 404 de um ID inexistente. Entidades JPA e hashes não são expostos.

## Importação CSV

```http
POST /api/importacoes?tipo=VENDAS
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data

arquivo: <arquivo CSV>
```

O campo multipart chama-se **`arquivo`**; `tipo` é `VENDAS` ou `RECEBIMENTOS`. Resposta preservada: `id,arquivo,tipo,status,linhasImportadas,linhasComErro` (HTTP 200).

Exemplo de vendas (`exemplos/vendas.csv`):

```csv
referencia;data;descricao;valor
VENDA-001;2026-10-13;Venda no cartão;120,00
VENDA-002;2026-10-13;Outra venda;1.234,56
```

Recebimentos (`exemplos/recebimentos.csv`):

```csv
referencia;data;descricao;valor
VENDA-001;2026-10-14;Crédito da operadora;120.00
VENDA-002;2026-10-14;Crédito da operadora;1230,00
```

Contrato preservado: cabeçalhos `referencia;data;descricao;valor` em minúsculas, separador ponto e vírgula, datas ISO `AAAA-MM-DD`, UTF-8 (BOM opcional), campos entre aspas conforme CSV. Linhas vazias são ignoradas; a numeração de erro continua baseada no número lógico do registro mais o cabeçalho. Cabeçalhos extras são tolerados. Data, descrição e valor são obrigatórios; referência vazia é aceita para posterior classificação `REFERENCIA_AUSENTE`.

Valores positivos aceitam decimal com ponto (`120.00`) ou vírgula (`120,00`). Quando existe vírgula, os pontos são interpretados como separadores de milhar (`1.234,56` = `1234.56`). Sem vírgula, ponto é decimal. Por compatibilidade, a conversão também mantém a sintaxe numérica aceita por `BigDecimal` (por exemplo notação científica); não houve redefinição silenciosa dessa regra. Agora valores com fração menor que centavos ou mais de 13 dígitos inteiros são rejeitados por linha para evitar arredondamento/erro no banco.

Limites: 5 MiB por arquivo, 6 MiB por requisição, 20.000 registros por arquivo, referência e campo valor até 100 caracteres, descrição até 255, nome até 255. O nome precisa terminar em `.csv`. MIME aceito: `text/csv`, `application/csv`, `text/plain`, `application/vnd.ms-excel`, `application/octet-stream` ou ausente; extensão/MIME não dispensam validação UTF-8, cabeçalhos e conteúdo. Conteúdo binário/caracteres de controle inválidos são recusados. Arquivos inválidos não deixam importação parcial persistida; erros de linha geram `CONCLUIDA_COM_ERROS` e preservam as linhas válidas em uma transação.

| Método e rota | Filtros e resposta |
| --- | --- |
| `GET /api/importacoes` | `page,size,tipo,status`; página de resumos da empresa |
| `GET /api/importacoes/{id}/erros` | Lista de `numeroLinha,mensagem`, após verificar propriedade da importação |

**Mudança de contrato intencional:** a listagem de importações passou de array para página `{content,page,size,totalElements,totalPages}`. `page` começa em zero; `size` padrão 20, entre 1 e 100. Ordem: ID decrescente. O cliente deve adaptar a leitura a `content`. A listagem de erros mantém array, limitado pelo teto de registros do arquivo.

## Conciliação, histórico e dashboard

| Método e rota | Contrato |
| --- | --- |
| `POST /api/conciliacoes?importacaoVendasId=1&importacaoRecebimentosId=2` | Lista imediata preservada de `referencia,valorVenda,valorRecebido,diferenca,status`; HTTP 200; ID da execução em `X-Conciliacao-Id` e rota em `Location` |
| `GET /api/conciliacoes` | Página de `id,importacaoVendasId,importacaoRecebimentosId,criadaEm`; filtros `importacaoId,desde,ate,page,size` |
| `GET /api/conciliacoes/{id}` | Metadados da execução, somente da empresa autenticada |
| `GET /api/conciliacoes/{id}/resultados` | Página dos resultados; filtros `status,referencia,page,size` |
| `GET /api/dashboard?conciliacaoId=1` | Contagens da execução escolhida; sem ID usa a mais recente da empresa; sem histórico retorna zeros |

`desde`/`ate` são instantes ISO com timezone (ex.: `2026-10-13T00:00:00Z`), limites inclusivos. Referência no filtro de resultados é exata após trim/maiúsculas. Resultados e listagens são paginados com a mesma estrutura das importações. Cada POST cria um snapshot de resultados e atualiza status dos lançamentos atomicamente. Repetições criam novo histórico; resultados antigos não são recalculados.

Referências são comparadas sem espaços nas pontas e sem diferenciar maiúsculas/minúsculas (`Locale.ROOT`). Valores iguais (desconsiderando escala) produzem `CONCILIADO`; diferentes produzem `DIVERGENTE`, com diferença **recebido menos venda**. Outras classificações: `VENDA_SEM_RECEBIMENTO`, `RECEBIMENTO_SEM_VENDA`, `REFERENCIA_DUPLICADA`, `REFERENCIA_AUSENTE`. Diferença é `null` nessas quatro situações; valores de um lado inexistente também são `null`. Qualquer resultado diferente de `CONCILIADO` marca seus lançamentos como `DIVERGENTE`, conforme comportamento anterior.

**Pagamentos parciais e possíveis duplicidades:** uma única venda permite vários recebimentos da referência, desde que tenham combinações distintas de **data, valor e descrição**. Valor é comparado sem diferença de escala; descrição usa trim/maiúsculas. Soma-se apenas esse grupo distinto para comparar com a venda: soma igual concilia; menor ou maior diverge. Duas vendas da mesma referência ou recebimentos com a mesma combinação são `REFERENCIA_DUPLICADA`, exigindo revisão, mesmo se a soma bruta coincidir com a venda. Nada é descartado. A soma bruta pode aparecer para inspeção, mas não torna um grupo suspeito conciliado. Sem um identificador transacional/parcelamento na origem, recebimentos legitimamente iguais no mesmo dia podem gerar falso positivo; o critério é conservador e explícito. Não há compensação de taxas/cancelamentos ou resolução automática de duplicidade.

O dashboard preserva a consulta sem filtros (última execução) e `conciliacaoId`. Com `historico=true` e/ou `desde`/`ate`, considera a **última execução de cada par vendas/recebimentos dentro do período de criação das conciliações**, inclusive os limites. Sem limites, `historico=true` consulta todo o histórico da empresa. Reexecuções do mesmo par não duplicam os totais. Retorna os campos anteriores mais `escopo,desde,ate,execucoes,importacoesSobrepostas`. São contagens de resultados por status, não saldos monetários. Se uma importação é usada em pares diferentes, `importacoesSobrepostas=true` alerta que há cenários sobrepostos; os totais são de resultados desses cenários e não um saldo contábil único. Não some-os como dinheiro. Não misture `conciliacaoId` com filtros de período. Exemplo: `GET /api/dashboard?historico=true&desde=2026-10-01T00:00:00Z&ate=2026-10-31T23:59:59Z`.

## Recuperação de senha e proteção contra abuso

`POST /api/auth/recuperar-senha` recebe `{"email":"admin@example.com"}` e retorna 202 com a mesma mensagem para conta inexistente, inativa ou existente. Para conta ativa, envia link por SMTP. Em desenvolvimento o e-mail é capturado no Mailpit; não sai para a internet. O link expira em 20 minutos e contém o token no fragmento `#token=...`, sem expor em access logs HTTP. O futuro frontend deverá ler/remover o fragmento e enviar `POST /api/auth/redefinir-senha` com `{"token":"<token recebido por e-mail>","senha":"<nova senha>"}`. Sucesso retorna 204. O token tem 256 bits aleatórios; somente SHA-256 é salvo. Consumo e troca de senha têm transação e bloqueios; o token não pode ser reutilizado, e a troca invalida JWTs e outros links anteriores. A nova V3 cria essa tabela sem alterar V1/V2. Falha de SMTP reverte o token e mantém resposta genérica; o envio tem timeout e não possui fila/retry durável. Resposta genérica não significa tempo de resposta constante.

Limites locais em memória: 15 requisições/minuto por IP e rota de autenticação, 10 logins/minuto por e-mail normalizado, 10 uploads/minuto por usuário e 30 por IP. Excesso retorna **429** com `Retry-After: 60` e envelope de erro da API, antes de processar o CSV. Headers `X-Forwarded-For` livres não burlam a contagem. Chaves são hashes para IP/e-mail, com limite global de memória. Configuração opcional: `AUTH_RATE_LIMIT`, `LOGIN_ACCOUNT_RATE_LIMIT`, `UPLOAD_RATE_LIMIT`. Em múltiplas réplicas, acrescente controle distribuído/na borda; reiniciar o processo zera contadores. Corpos de autenticação com Content-Length acima de 16 KiB são recusados. Não habilite logs de corpos de login/recuperação.

Configuração SMTP externa opcional no perfil padrão: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `EMAIL_FROM`, `RESET_PASSWORD_URL`. O Compose já configura os valores locais. Para domínio/e-mail real e HTTPS, consulte [docs/implantacao.md](docs/implantacao.md). No perfil `prod`, a recuperação fica desabilitada por padrão; habilitá-la exige configuração externa e SMTP autenticado com STARTTLS.

## Backup e restauração local

Com o Compose iniciado e sem escritas concorrentes:

```powershell
$backup = .\scripts\backup.ps1
.\scripts\restore-test.ps1 -Backup $backup
```

O dump é salvo em `.local/backups` (ignorado) e restaurado em **outro container**, em banco novo, sem sobrescrever o banco do app. São comparadas contagens das tabelas e histórico Flyway. Consulte a documentação de implantação para retenção, criptografia e recuperação fora da máquina. O script de teste não é uma autorização para restaurar sobre uma base real.

## Erros, CORS e Swagger

Erros da API têm `timestamp,status,error,message,path`. Principais códigos: 400 entrada inválida, 401 sem autenticação/token inválido/login incorreto, 403 papel sem acesso, 404 recurso inexistente ou de outra empresa, 409 e-mail indisponível/conflito, 413 tamanho excedido e 415 conteúdo não aceito. Não são devolvidos stack traces, SQL, senhas ou tokens em erros. Falhas internas retornam mensagem genérica. Rejeições CORS podem ser respondidas diretamente pelo filtro do framework, sem envelope JSON.

Swagger: `http://localhost:8080/swagger-ui/index.html`; contrato gerado pelas anotações/DTOs: `http://localhost:8080/v3/api-docs`. Esses dois recursos são públicos para permitir testar cadastro/login. Execute login, copie apenas o `accessToken`, clique em **Authorize** e cole o JWT (sem prefixar `Bearer` nesse campo). As demais operações usam Bearer. A especificação gerada é a fonte do contrato; não há cópia OpenAPI manual divergente.

CORS permite apenas as origens configuradas, headers `Authorization`/`Content-Type`, métodos usados pela API e expõe `Location`/`X-Conciliacao-Id`. Não usa credenciais de cookie. Para produção configure a origem HTTPS exata do frontend e forneça HTTPS na infraestrutura.

## Testes isolados

**Nenhum teste usa as variáveis de banco de desenvolvimento como fallback.** Por padrão, Testcontainers inicia PostgreSQL 18 e Mailpit descartáveis, com portas aleatórias, segredos de teste aleatórios e coleta automática ao encerrar a JVM. Execute diretamente, sem preencher variáveis:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
git diff --check
```

Para banco externo explicitamente separado, `TestDatabase` aceita `TEST_DB_URL` apenas com banco chamado `confere_test...`, sem query params, diferente de `DB_URL`. Cada contexto/teste de migration usa esquema aleatório próprio. Nunca usa os dados do Compose como banco de teste. O Mailpit de testes continua em container separado. Não há `clean` Flyway, exclusão nem truncamento de tabelas existentes.

No Windows, com binários PostgreSQL instalados:

```powershell
.\scripts\test.ps1 -PgBin 'C:\Program Files\PostgreSQL\18\bin'
```

O script cria um cluster **novo** em `target/pg-test-<id>`, escolhe porta livre, escuta somente em `127.0.0.1`, cria `confere_test`, executa `mvnw test` e encerra a instância em `finally`. Usa autenticação `trust` somente nessa instância temporária de loopback, sem credenciais persistidas. Execute em máquina de desenvolvimento controlada, sem outros usuários não confiáveis. Não opera o serviço PostgreSQL instalado. Os artefatos ignorados em `target` ficam disponíveis para diagnóstico; podem ser removidos manualmente após confirmar que o processo foi encerrado. Não reutilize esse cluster em produção.

Alternativa multiplataforma: provisione uma instância/banco de teste **separado**, conceda permissão de criar esquemas a um usuário exclusivo e defina `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` no ambiente. Execute `./mvnw test` (ou `.\mvnw.cmd test`). Uma base com prefixo de teste é uma proteção adicional, não substitui o provisionamento separado. Docker deve estar ativo para o capturador SMTP dos testes de integração. Se não estiver disponível, os testes de integração falham explicitamente; não buscam senhas locais nem mudam para o banco de desenvolvimento.

Somente unitários, sem PostgreSQL:

```powershell
.\scripts\test.ps1 -UnitOnly
```

Cobertura: regras de conciliação, duplicidades e referências ausentes; cadastro/login/BCrypt; validação de CSV; autenticação e gestão de usuários; revogação de tokens; bootstrap idempotente; migração V1 com dados para V2; histórico/dashboard; Swagger/CORS; e isolamento de listagem, erros, conciliação, histórico, resultados, dashboard e usuários entre empresas. Integração sobe o aplicativo real em porta aleatória e chama endpoints por HTTP. Não modifica o banco de desenvolvimento.

## Limitações antes de uso real

MVP para integração local, **sem declaração de prontidão para produção**. Pagamentos parciais têm critério explícito, mas faltam identificadores transacionais para eliminar falsos positivos de duplicidade; taxas e cancelamentos ainda não são tratados. O dashboard consolida por par no período e sinaliza sobreposições, sem produzir saldo contábil único. Não há verificação de e-mail, MFA, refresh tokens, convites, exclusão de empresas, edição de e-mail ou auditoria completa de alterações de usuários. Rate limiting é por instância; recuperação não tem fila durável de e-mails. Domínio/certificado público, provedor SMTP real, retenção/LGPD, backups fora da máquina, monitoramento sem dados sensíveis e revisão operacional continuam necessários para implantação. O banco é compartilhado com isolamento na aplicação (sem RLS); controle o acesso SQL direto. Importação e resposta imediata usam memória com limites; contagens por item da listagem podem ser otimizadas conforme escala. Reconciliar atualiza o status atual dos lançamentos; os snapshots preservam seus resultados anteriores. Não há idempotência de POST. Veja detalhes em [docs/implantacao.md](docs/implantacao.md).
