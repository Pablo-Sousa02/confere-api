package com.pablosousa.confere;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MigrationTests {
    @Test void preservaDadosDaV1EAtribuiEmpresaInicialSemCriarCredenciais() throws Exception {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure().dataSource(TestDatabase.url(), TestDatabase.usuario(), TestDatabase.senha())
                .schemas(schema).defaultSchema(schema);
        config.target("1").load().migrate();
        try (var c = DriverManager.getConnection(TestDatabase.url(), TestDatabase.usuario(), TestDatabase.senha()); var s = c.createStatement()) {
            s.execute("SET search_path TO " + schema);
            s.execute("INSERT INTO importacoes(id,nome_arquivo,tipo,status) VALUES (100,'antigo.csv','VENDAS','CONCLUIDA')");
            s.execute("INSERT INTO lancamentos(importacao_id,numero_linha,data_movimento,valor) VALUES(100,2,'2026-10-13',10)");
            s.execute("INSERT INTO erros_importacao(importacao_id,numero_linha,mensagem) VALUES(100,3,'Valor inválido')");
        }
        config.target("latest").load().migrate();
        try (var c = DriverManager.getConnection(TestDatabase.url(), TestDatabase.usuario(), TestDatabase.senha()); var s = c.createStatement()) {
            s.execute("SET search_path TO " + schema);
            try (var r = s.executeQuery("SELECT i.nome_arquivo,e.inicial,(SELECT count(*) FROM usuarios), (SELECT count(*) FROM lancamentos), (SELECT count(*) FROM erros_importacao) FROM importacoes i JOIN empresas e ON e.id=i.empresa_id WHERE i.id=100")) {
                assertTrue(r.next()); assertEquals("antigo.csv", r.getString(1)); assertTrue(r.getBoolean(2));
                assertEquals(0, r.getLong(3)); assertEquals(1, r.getLong(4)); assertEquals(1, r.getLong(5));
            }
        }
        config.load().validate();
    }
}
