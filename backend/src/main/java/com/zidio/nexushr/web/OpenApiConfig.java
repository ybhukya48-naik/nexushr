package com.zidio.nexushr.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nexusHrOpenApi() {
        final String bearerSchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("NexusHR API")
                        .description("""
                                AI-Enabled Enterprise HR and Workforce Intelligence Platform.

                                **Authentication:** Use `POST /api/v1/auth/login` to obtain a JWT access token,
                                then click **Authorize** and enter `Bearer <token>`.

                                **Demo users:** `admin`, `hr`, `manager`, `employee` (any password).
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Zidio Development")
                                .url("https://zidio.in"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("/").description("Current server")))
                .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
                .components(new Components()
                        .addSecuritySchemes(bearerSchemeName,
                                new SecurityScheme()
                                        .name(bearerSchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste the JWT from /api/v1/auth/login here")));
    }
}
