package com.pablosousa.confere;

import com.pablosousa.confere.auth.BootstrapAdmin;
import com.pablosousa.confere.empresa.EmpresaRepository;
import com.pablosousa.confere.usuario.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTests {
    @DynamicPropertySource static void banco(DynamicPropertyRegistry r) { TestDatabase.configurar(r); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired PlatformTransactionManager transactionManager;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private String tokenA, tokenB, senha, emailA;

    @BeforeEach void cadastrarEmpresas() throws Exception {
        senha = UUID.randomUUID().toString();
        emailA = UUID.randomUUID() + "@example.com";
        tokenA = cadastrar("Empresa A", emailA, senha);
        tokenB = cadastrar("Empresa B", UUID.randomUUID() + "@example.com", senha);
    }

    @Test void cadastroLoginNormalizacaoEHash() throws Exception {
        var response = json("POST", "/api/auth/login", null, Map.of("email", " " + emailA.toUpperCase(Locale.ROOT) + " ", "senha", senha), 200);
        assertEquals("Bearer", response.get("tokenType").asText());
        var hash = jdbc.queryForObject("select senha_hash from usuarios where email=?", String.class, emailA);
        assertNotEquals(senha, hash); assertTrue(hash.startsWith("$2a$")); assertTrue(encoder.matches(senha, hash));
        json("POST", "/api/auth/login", null, Map.of("email", emailA, "senha", UUID.randomUUID().toString()), 401);
        json("POST", "/api/auth/login", null, Map.of("email", UUID.randomUUID() + "@example.com", "senha", senha), 401);
        json("POST", "/api/auth/cadastro", null, Map.of("empresa", "Outra", "nome", "Admin", "email", emailA.toUpperCase(Locale.ROOT), "senha", senha), 409);
    }

    @Test void exigeAutenticacaoERejeitaTokenAdulterado() throws Exception {
        for (String path : List.of("/api/importacoes", "/api/conciliacoes", "/api/dashboard", "/api/usuarios")) request("GET", path, null, null, null, 401);
        request("POST", "/api/importacoes?tipo=VENDAS", null, new byte[0], "multipart/form-data; boundary=test", 401);
        request("POST", "/api/conciliacoes?importacaoVendasId=1&importacaoRecebimentosId=2", null, null, null, 401);
        request("GET", "/api/importacoes", tokenA.substring(0, tokenA.length() - 8) + "xxxxxxxx", null, null, 401);
        var original = jwtDecoder.decode(tokenA);
        var expirado = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .claims(c -> c.putAll(original.getClaims())).expiresAt(java.time.Instant.now().minusSeconds(120))
                .issuedAt(java.time.Instant.now().minusSeconds(3600)).build();
        String tokenExpirado = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), expirado)).getTokenValue();
        request("GET", "/api/importacoes", tokenExpirado, null, null, 401);
        var outraEmpresa = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .claims(c -> c.putAll(original.getClaims())).claim("empresaId", -1L).build();
        String tokenOutraEmpresa = jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), outraEmpresa)).getTokenValue();
        request("GET", "/api/importacoes", tokenOutraEmpresa, null, null, 401);
    }

    @Test void importaConciliaEPersisteHistoricoComIsolamento() throws Exception {
        long vendas = importar(tokenA, "VENDAS", "referencia;data;descricao;valor\nA;2026-10-13;Venda;10,00\nB;2026-10-13;Venda;20\nC;2026-10-13;Venda;30\nINVALIDA;sem-data;Venda;10\n");
        long recebimentos = importar(tokenA, "RECEBIMENTOS", "referencia;data;descricao;valor\na;2026-10-13;Recebimento;10.00\nB;2026-10-13;Recebimento;18\nD;2026-10-13;Recebimento;40\n");
        var listA = json("GET", "/api/importacoes?tipo=VENDAS&status=CONCLUIDA_COM_ERROS&size=1", tokenA, null, 200);
        assertEquals(1, listA.get("totalElements").asLong()); assertEquals(vendas, listA.get("content").get(0).get("id").asLong());
        var listB = json("GET", "/api/importacoes", tokenB, null, 200);
        assertEquals(0, listB.get("totalElements").asLong());
        assertEquals(1, json("GET", "/api/importacoes/" + vendas + "/erros", tokenA, null, 200).size());
        request("GET", "/api/importacoes/" + vendas + "/erros", tokenB, null, null, 404);
        request("POST", conciliarPath(vendas, recebimentos), tokenB, null, null, 404);
        long vendaB = importar(tokenB, "VENDAS", csv());
        request("POST", conciliarPath(vendaB, recebimentos), tokenB, null, null, 404);
        request("POST", conciliarPath(vendas, recebimentos), tokenA, null, null, 200);
        var historico = json("GET", "/api/conciliacoes?importacaoId=" + vendas, tokenA, null, 200);
        long id = historico.get("content").get(0).get("id").asLong();
        assertEquals(1, historico.get("totalElements").asLong());
        var resultados = json("GET", "/api/conciliacoes/" + id + "/resultados?status=CONCILIADO&referencia=a", tokenA, null, 200);
        assertEquals(1, resultados.get("totalElements").asLong());
        assertEquals("CONCILIADO", resultados.get("content").get(0).get("status").asText());
        var dashboard = json("GET", "/api/dashboard", tokenA, null, 200);
        for (String campo : List.of("conciliados", "divergentes", "vendasSemRecebimento", "recebimentosSemVenda")) assertEquals(1, dashboard.get(campo).asLong());
        assertEquals(0, json("GET", "/api/conciliacoes", tokenB, null, 200).get("totalElements").asLong());
        for (String path : List.of("/api/conciliacoes/" + id, "/api/conciliacoes/" + id + "/resultados", "/api/dashboard?conciliacaoId=" + id))
            request("GET", path, tokenB, null, null, 404);
        request("POST", conciliarPath(recebimentos, vendas), tokenA, null, null, 400);
        request("POST", conciliarPath(vendas, recebimentos), tokenA, null, null, 200);
        assertEquals(2, json("GET", "/api/conciliacoes", tokenA, null, 200).get("totalElements").asLong());
        assertEquals(1, json("GET", "/api/dashboard", tokenA, null, 200).get("conciliados").asLong());
    }

    @Test void validaArquivoConteudoELimitesSemPersistirFalhas() throws Exception {
        upload(tokenA, "arquivo.csv", "image/png", csv().getBytes(StandardCharsets.UTF_8), 415);
        upload(tokenA, "arquivo.exe", "text/csv", csv().getBytes(StandardCharsets.UTF_8), 400);
        upload(tokenA, "arquivo.csv", "text/csv", "cabecalho;errado\n".getBytes(StandardCharsets.UTF_8), 400);
        upload(tokenA, "arquivo.csv", "text/csv", new byte[]{(byte)0xff}, 400);
        upload(tokenA, "arquivo.csv", "text/csv", new byte[]{0,1,2}, 400);
        upload(tokenA, "arquivo.csv", "text/csv", new byte[0], 400);
        upload(tokenA, "arquivo.csv", "text/csv", new byte[5*1024*1024+1], 413);
        assertEquals(0, json("GET", "/api/importacoes", tokenA, null, 200).get("totalElements").asLong());
        long id = importar(tokenA, "VENDAS", "referencia;data;descricao;valor\nA;2026-10-13;Venda;0\nB;2026-10-13;Venda;1.234\nC;2026-10-13;Venda;10000000000000\nD;2026-10-13;Venda;1E+1000000000\nE;2026-10-13;Venda;1E-1000000000\n");
        assertEquals(5, json("GET", "/api/importacoes/" + id + "/erros", tokenA, null, 200).size());
    }

    @Test void papéisGestaoDeUsuariosERevogacaoDeTokens() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var novo = json("POST", "/api/usuarios", tokenA, Map.of("nome", "Analista", "email", email, "senha", senha, "papel", "ANALISTA"), 201);
        long id = novo.get("id").asLong();
        String analista = json("POST", "/api/auth/login", null, Map.of("email", email, "senha", senha), 200).get("accessToken").asText();
        request("GET", "/api/usuarios", analista, null, null, 403);
        long venda = importar(analista, "VENDAS", csv());
        request("GET", "/api/importacoes/" + venda + "/erros", analista, null, null, 200);
        json("PUT", "/api/usuarios/" + id, tokenB, Map.of("nome", "Outro", "papel", "ADMIN", "ativo", true), 404);
        json("PUT", "/api/usuarios/" + id, tokenA, Map.of("nome", "Analista", "papel", "ANALISTA", "ativo", false), 200);
        request("GET", "/api/importacoes", analista, null, null, 401);
        json("POST", "/api/auth/login", null, Map.of("email", email, "senha", senha), 401);
        var lista = json("GET", "/api/usuarios", tokenA, null, 200).get("content");
        long admin = 0;
        for (var u : lista) { assertFalse(u.has("senhaHash")); if (u.get("email").asText().equals(emailA)) admin = u.get("id").asLong(); }
        json("PUT", "/api/usuarios/" + admin, tokenA, Map.of("nome", "Admin", "papel", "ANALISTA", "ativo", true), 400);
    }

    @Test void naoAceitaIdentidadeForjadaNemCamposDesconhecidos() throws Exception {
        json("POST", "/api/auth/cadastro", null, Map.of("empresa", "Fraude", "nome", "Admin", "email", UUID.randomUUID() + "@example.com", "senha", senha, "empresaId", 1), 400);
        json("POST", "/api/usuarios", tokenA, Map.of("nome", "Outro", "email", UUID.randomUUID() + "@example.com", "senha", senha, "papel", "SUPERADMIN"), 400);
        json("POST", "/api/auth/cadastro", null, Map.of("empresa", "X", "nome", "", "email", "invalido", "senha", UUID.randomUUID().toString().substring(0, 5)), 400);
        request("GET", "/api/importacoes?size=101", tokenA, null, null, 400);
        request("GET", "/api/importacoes?page=-1", tokenA, null, null, 400);
        request("GET", "/api/importacoes?tipo=INVALIDO", tokenA, null, null, 400);
    }

    @Test void swaggerECors() throws Exception {
        var spec = mapper.readTree(request("GET", "/v3/api-docs", null, null, null, 200).body());
        assertEquals("bearer", spec.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asText());
        assertTrue(spec.get("paths").has("/api/importacoes")); assertTrue(spec.get("paths").has("/api/auth/login"));
        assertTrue(spec.get("paths").get("/api/auth/login").get("post").get("security").isEmpty());
        request("GET", "/swagger-ui/index.html", null, null, null, 200);
        var preflight = HttpRequest.newBuilder(URI.create(base() + "/api/importacoes"))
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var permitido = http.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, permitido.statusCode()); assertEquals("http://localhost:5173", permitido.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        var proibido = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/importacoes")).header("Origin", "https://nao-permitido.example")
                .header("Access-Control-Request-Method", "GET").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, proibido.statusCode()); assertTrue(proibido.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test void bootstrapInicialSeguroEIdempotente() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var bootstrap = new BootstrapAdmin(empresas, usuarios, encoder, "Administrador inicial", email, senha);
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> bootstrap.run(null));
        tx.executeWithoutResult(s -> bootstrap.run(null));
        assertEquals(1, jdbc.queryForObject("select count(*) from usuarios u join empresas e on e.id=u.empresa_id where e.inicial", Integer.class));
        var login = json("POST", "/api/auth/login", null, Map.of("email", email, "senha", senha), 200);
        assertNotNull(login.get("accessToken"));
    }

    @Test void parciaisEDashboardPorPeriodoSemContarReexecucao() throws Exception {
        long v = importar(tokenA, "VENDAS", csv());
        long r = importar(tokenA, "RECEBIMENTOS", "referencia;data;descricao;valor\nA;2026-10-13;Parcela 1;4\nA;2026-10-14;Parcela 2;6\n");
        for (int i=0; i<2; i++) {
            var resultados = mapper.readTree(request("POST", conciliarPath(v, r), tokenA, null, null, 200).body());
            assertEquals("CONCILIADO", resultados.get(0).get("status").asText());
        }
        var periodo = json("GET", "/api/dashboard?historico=true&desde=2026-01-01T00:00:00Z&ate=2100-01-01T00:00:00Z", tokenA, null, 200);
        assertEquals(1, periodo.get("execucoes").asLong()); assertEquals(1, periodo.get("conciliados").asLong());
        assertFalse(periodo.get("importacoesSobrepostas").asBoolean());
        assertEquals(0, json("GET", "/api/dashboard?historico=true", tokenB, null, 200).get("execucoes").asLong());
        assertEquals(0, json("GET", "/api/dashboard?ate=2000-01-01T00:00:00Z", tokenA, null, 200).get("execucoes").asLong());
        long r2 = importar(tokenA, "RECEBIMENTOS", csv());
        request("POST", conciliarPath(v,r2), tokenA, null, null, 200);
        assertTrue(json("GET", "/api/dashboard?historico=true", tokenA, null, 200).get("importacoesSobrepostas").asBoolean());
        request("GET", "/api/dashboard?desde=2100-01-01T00:00:00Z&ate=2000-01-01T00:00:00Z", tokenA, null, null, 400);
    }

    @Test void recuperacaoPorSmtpUsoUnicoExpiracaoERevogacao() throws Exception {
        var resposta = json("POST", "/api/auth/recuperar-senha", null, Map.of("email", emailA), 202);
        var inexistente = json("POST", "/api/auth/recuperar-senha", null, Map.of("email", UUID.randomUUID()+"@example.com"), 202);
        assertEquals(resposta, inexistente);
        String token = tokenRecebido(emailA);
        String hash = jdbc.queryForObject("select token_hash from recuperacoes_senha r join usuarios u on u.id=r.usuario_id where u.email=? order by r.id desc limit 1", String.class, emailA);
        assertEquals(com.pablosousa.confere.auth.RecuperacaoService.hash(token), hash);
        assertFalse(token.equals(hash));
        String novaSenha = UUID.randomUUID().toString();
        request("POST", "/api/auth/redefinir-senha", null, mapper.writeValueAsBytes(Map.of("token",token,"senha",novaSenha)), "application/json", 204);
        json("POST", "/api/auth/redefinir-senha", null, Map.of("token",token,"senha",novaSenha), 400);
        request("GET", "/api/importacoes", tokenA, null, null, 401);
        request("GET", "/api/importacoes", tokenB, null, null, 200);
        json("POST", "/api/auth/login", null, Map.of("email",emailA,"senha",senha), 401);
        json("POST", "/api/auth/login", null, Map.of("email",emailA,"senha",novaSenha), 200);
        json("POST", "/api/auth/recuperar-senha", null, Map.of("email",emailA), 202);
        String expirado = tokenRecebido(emailA);
        jdbc.update("update recuperacoes_senha set expira_em=current_timestamp - interval '1 minute' where token_hash=?", com.pablosousa.confere.auth.RecuperacaoService.hash(expirado));
        json("POST", "/api/auth/redefinir-senha", null, Map.of("token",expirado,"senha",UUID.randomUUID().toString()), 400);
    }

    private String tokenRecebido(String destino) throws Exception {
        String query = java.net.URLEncoder.encode("to:"+destino, StandardCharsets.UTF_8);
        var lista = http.send(HttpRequest.newBuilder(URI.create(TestDatabase.mailUrl()+"/api/v1/search?query="+query)).GET().build(), HttpResponse.BodyHandlers.ofString());
        var mensagens = mapper.readTree(lista.body()).get("messages");
        assertNotNull(mensagens); assertFalse(mensagens.isEmpty());
        String id = mensagens.get(0).get("ID").asText();
        var resposta = http.send(HttpRequest.newBuilder(URI.create(TestDatabase.mailUrl()+"/api/v1/message/"+id)).GET().build(), HttpResponse.BodyHandlers.ofString());
        String texto = mapper.readTree(resposta.body()).get("Text").asText();
        var matcher = java.util.regex.Pattern.compile("#token=([A-Za-z0-9_-]{43})").matcher(texto);
        assertTrue(matcher.find(), "E-mail deve conter link de recuperação.");
        return matcher.group(1);
    }

    private String cadastrar(String empresa, String email, String senha) throws Exception {
        return json("POST", "/api/auth/cadastro", null, Map.of("empresa", empresa, "nome", "Administrador", "email", email, "senha", senha), 201).get("accessToken").asText();
    }
    private String base() { return "http://127.0.0.1:" + port; }
    private String csv() { return "referencia;data;descricao;valor\nA;2026-10-13;Venda;10,00\n"; }
    private String conciliarPath(long vendas, long recebimentos) { return "/api/conciliacoes?importacaoVendasId=" + vendas + "&importacaoRecebimentosId=" + recebimentos; }
    private JsonNode json(String method, String path, String token, Object body, int status) throws Exception {
        return mapper.readTree(request(method, path, token, body == null ? null : mapper.writeValueAsBytes(body), "application/json", status).body());
    }
    private HttpResponse<String> request(String method, String path, String token, byte[] body, String type, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base() + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (type != null) builder.header("Content-Type", type);
        var response = http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path);
        return response;
    }
    private long importar(String token, String tipo, String csv) throws Exception {
        return mapper.readTree(upload(token, "arquivo.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8), 200, tipo).body()).get("id").asLong();
    }
    private HttpResponse<String> upload(String token, String nome, String mime, byte[] bytes, int status) throws Exception { return upload(token, nome, mime, bytes, status, "VENDAS"); }
    private HttpResponse<String> upload(String token, String nome, String mime, byte[] bytes, int status, String tipo) throws Exception {
        String boundary = "confere" + UUID.randomUUID();
        var out = new java.io.ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"" + nome + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes); out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return request("POST", "/api/importacoes?tipo=" + tipo, token, out.toByteArray(), "multipart/form-data; boundary=" + boundary, status);
    }
}
