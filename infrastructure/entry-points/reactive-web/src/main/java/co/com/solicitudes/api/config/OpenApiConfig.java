package co.com.solicitudes.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "CrediYa - solicitudes API", version = "v1", description = "APIs soliciud para solicitud de créditos para los usuarios"))
public class OpenApiConfig {
}



