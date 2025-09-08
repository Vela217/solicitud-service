package co.com.solicitudes.api.config;

import co.com.solicitudes.api.dto.GenericResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Log4j2
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor // <--- agrega esto
public class AuthorizationJwt implements WebFluxConfigurer {

    private final ObjectMapper mapper;

    @Value("${jwt.json-exp-roles:/roles}")
    private String jsonExpRoles;

    private static final String ROLE = "ROLE_";

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> authConverter) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(reg -> reg
                        .pathMatchers("/v1/api-docs/**","/swagger-ui.html","/swagger-ui/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/solicitud").hasRole("CLIENTE")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .accessDeniedHandler(accessDeniedHandler())
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(authConverter)))

                .build();
    }

    // Valida tokens RS256 usando la clave pública local
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${security.jwt.public}") RSAPublicKey publicKey) {
        log.info("ReactiveJwtDecoder inicializado (firma RS256 + validación exp/nbf por defecto)");
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        // Deja explícito que usamos validadores por defecto (incluye JwtTimestampValidator con tolerancia estándar)
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        var jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null || roles.isEmpty()) {
                String single = jwt.getClaimAsString("roles");
                if (single != null) {
                    roles = java.util.Arrays.stream(single.split(","))
                            .map(String::trim).filter(s -> !s.isBlank())
                            .toList();
                } else {
                    roles = List.of();
                }
            }

            log.info("Roles extraídos del JWT: {}", roles);

            return roles.stream()
                    .map(r -> "ROLE_" + r) // => ROLE_ADMINISTRADOR
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
    }


    // --- 401 personalizado: distingue "token expirado" ---
    @Bean
    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> {
            // Útil para inspeccionar qué excepción llega realmente
            log.warn("AuthEntryPoint ex={} msg={} cause={}",
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getClass().getName() : "null");

            String message = "Token inválido o no provisto";
            // Caso más común en Resource Server: OAuth2AuthenticationException con BearerTokenError
            if (ex instanceof OAuth2AuthenticationException oae) {
                var err = oae.getError();
                var desc = Optional.ofNullable(err.getDescription()).orElse("").toLowerCase();
                if (desc.contains("expired") || desc.contains("expir")) {
                    message = "El token ha expirado";
                } else {
                    message = "Token inválido";
                }

                // A veces llega encadenada como JwtValidationException
            } else if (ex.getCause() instanceof JwtValidationException jve) {
                boolean expired = jve.getErrors().stream()
                        .map(err -> Optional.ofNullable(err.getDescription()).orElse("").toLowerCase())
                        .anyMatch(d -> d.contains("expired") || d.contains("expir"));
                message = expired ? "El token ha expirado" : "Token inválido";

                // Fallback: algunos adaptadores ponen el texto en el message
            } else if (Optional.ofNullable(ex.getMessage()).orElse("").toLowerCase().contains("expired")) {
                message = "El token ha expirado";
            }

            var dto = GenericResponseDto.builder()
                    .success(false)
                    .message(message)
                    .statusCode(HttpStatus.UNAUTHORIZED.value())
                    .data(null)
                    .build();

            return writeJson(exchange, dto, HttpStatus.UNAUTHORIZED);
        };
    }

    // --- 403 personalizado (autenticado, pero sin permisos) ---
    @Bean
    public ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, denied) -> {
            var dto = GenericResponseDto.builder()
                    .success(false)
                    .message("No tienes permisos para acceder a este recurso")
                    .statusCode(HttpStatus.FORBIDDEN.value())
                    .data(null)
                    .build();

            return writeJson(exchange, dto, HttpStatus.FORBIDDEN);
        };
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, Object body, HttpStatus status) {
        var res = exchange.getResponse();
        if (res.isCommitted()) return Mono.empty();

        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = mapper.writeValueAsBytes(body);
            return res.writeWith(Mono.just(res.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            var fallback = """
                    {"success":false,"message":"Error serializando respuesta","statusCode":500,"data":null}
                    """;
            var buf = res.bufferFactory().wrap(fallback.getBytes(StandardCharsets.UTF_8));
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return res.writeWith(Mono.just(buf));
        }
    }
}
