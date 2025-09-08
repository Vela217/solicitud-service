package co.com.solicitudes.consumer.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
@Configuration
public class RestConsumerConfig {

    private final String url;

    private final int timeout;

    public RestConsumerConfig(@Value("${adapters.restconsumer.url}") String url,
                              @Value("${adapters.restconsumer.timeout}") int timeout) {
        this.url = url;
        this.timeout = timeout;
    }

    @Bean
    public WebClient getWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl(url)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .clientConnector(getClientHttpConnector())
                .filter(bearerPropagator())   // 👈 añade el Bearer del SecurityContext
                .filter(logRequest())         // 👈 logs salientes
                .filter(logResponse())
                .build();
    }

    private ClientHttpConnector getClientHttpConnector() {
        /*
        IF YO REQUIRE APPEND SSL CERTIFICATE SELF SIGNED: this should be in the default cacerts trustore
        */
        return new ReactorClientHttpConnector(HttpClient.create()
                .compress(true)
                .keepAlive(true)
                .option(CONNECT_TIMEOUT_MILLIS, timeout)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(timeout, MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(timeout, MILLISECONDS));
                }));
    }

    @Bean
    public ExchangeFilterFunction bearerPropagator() {
        return (request, next) ->
                ReactiveSecurityContextHolder.getContext()
                        .map(SecurityContext::getAuthentication)
                        .cast(JwtAuthenticationToken.class)
                        .map(JwtAuthenticationToken::getToken)
                        .map(Jwt::getTokenValue)
                        .defaultIfEmpty("")  // no hay contexto/token → no agrega header
                        .flatMap(token -> {
                            ClientRequest.Builder rb = ClientRequest.from(request);
                            if (!token.isBlank()) {
                                log.debug("[authWebClient] → Propagando Bearer ({}...)",
                                        token.length() > 12 ? token.substring(0, 12) : "short");
                                rb.headers(h -> h.setBearerAuth(token));
                            } else {
                                log.warn("[authWebClient] ⚠ No hay SecurityContext/JWT para propagar");
                            }
                            return next.exchange(rb.build());
                        });
    }

    /** Logs de petición */
    @Bean
    public ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            log.info("[authWebClient] {} {}", req.method(), req.url());
            return reactor.core.publisher.Mono.just(req);
        });
    }

    /** Logs de respuesta */
    @Bean
    public ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(res -> {
            log.info("[authWebClient] ⇠ status={}", res.statusCode());
            return reactor.core.publisher.Mono.just(res);
        });
    }

}
