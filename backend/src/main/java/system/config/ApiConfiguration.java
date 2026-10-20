package system.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class ApiConfiguration {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("二手房业务管理 API").version("1.0"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerToken",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("opaque")))
                .addSecurityItem(new SecurityRequirement().addList("bearerToken"));
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer longIds() {
        return builder -> {
            builder.serializerByType(Long.class, new system.common.IdSerializer());
            builder.serializerByType(Long.TYPE, new system.common.IdSerializer());
        };
    }
}
