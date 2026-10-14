package com.pablosousa.confere.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Confere API").version("1.0")
                .description("API multiempresa. Obtenha um JWT em /api/auth/login e use Authorize. Listagens paginadas; page inicia em zero."))
                .servers(java.util.List.of(new io.swagger.v3.oas.models.servers.Server().url("/")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
