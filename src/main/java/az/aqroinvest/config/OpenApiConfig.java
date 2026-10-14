package az.aqroinvest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI aqroInvestOpenApi() {
        return new OpenAPI()
                .info(new Info().title("AqroInvest Qarabağ API").version("v1")
                        .description("Sahələr, müsabiqələr, müraciət statusu və xarici sistemlər üçün qərarlar. Bütün məlumatlar uydurmadır."))
                .components(new Components().addSecuritySchemes("apiKey",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")));
    }

    /** Only the REST API in Swagger, not the Thymeleaf controllers. */
    @Bean
    GroupedOpenApi restApi() {
        return GroupedOpenApi.builder().group("v1").pathsToMatch("/api/v1/**").build();
    }
}
