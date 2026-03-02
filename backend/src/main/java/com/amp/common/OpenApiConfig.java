package com.amp.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Marketing Platform API")
                        .version("0.1")
                        .description("REST API for AI Marketing Platform (Meta Ads)"))
                .addSecurityItem(new SecurityRequirement().addList("dev-headers"))
                .components(new Components()
                        .addSecuritySchemes("dev-headers", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Dev-User-Email")));
    }
}
