package com.berk.courier_tracking_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH_SCHEME = "Bearer Authentication";

    @Bean
    public OpenAPI courierTrackingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Courier Tracking API")
                        .description("""
                                Gerçek zamanlı kurye konum takibi, akıllı kurye atama \
                                ve sipariş yönetimi REST API'si.
                                Kimlik doğrulama için önce /api/v1/auth/login veya \
                                /api/v1/auth/register endpoint'lerinden JWT token alın, \
                                ardından Swagger UI'da 'Authorize' butonuna \
                                'Bearer <token>' formatında yapıştırın.""")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Courier Tracking Team")
                                .email("support@courier-tracking.local")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name(BEARER_AUTH_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token. Format: Bearer {token}")));
    }
}
