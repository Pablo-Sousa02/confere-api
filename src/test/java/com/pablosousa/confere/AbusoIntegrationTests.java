package com.pablosousa.confere;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AbusoIntegrationTests {
    @DynamicPropertySource static void banco(DynamicPropertyRegistry r) {
        TestDatabase.configurar(r); r.add("app.abuse.auth-per-minute", () -> 2); r.add("app.abuse.upload-per-minute", () -> 1);
    }
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    @Test void limitaLoginEUploadSemConfiarNoHeaderDeIp() throws Exception {
        for (int i=0;i<3;i++) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/login"))
                    .header("Content-Type","application/json").header("X-Forwarded-For","192.0.2."+i)
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(i<2 ? 400 : 429, response.statusCode());
            if (i==2) assertEquals("60", response.headers().firstValue("Retry-After").orElseThrow());
        }
        var mapper = JsonMapper.builder().build();
        byte[] cadastro = mapper.writeValueAsBytes(Map.of("empresa","Teste","nome","Admin","email",UUID.randomUUID()+"@example.com","senha",UUID.randomUUID().toString()));
        var registrado = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/cadastro"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(cadastro)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, registrado.statusCode());
        String token = mapper.readTree(registrado.body()).get("accessToken").asText();
        for (int i=0;i<2;i++) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/importacoes?tipo=VENDAS"))
                    .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary=test")
                    .POST(HttpRequest.BodyPublishers.ofString("--test--\r\n")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(i==0 ? 400 : 429, response.statusCode());
        }
    }
}
