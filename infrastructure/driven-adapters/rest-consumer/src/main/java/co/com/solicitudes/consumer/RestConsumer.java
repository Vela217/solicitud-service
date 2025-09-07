package co.com.solicitudes.consumer;


import co.com.solicitudes.model.authclient.AuthClient;
import co.com.solicitudes.model.authclient.gateways.IAuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.rmi.RemoteException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestConsumer implements IAuthClient/* implements Gateway from domain */ {
    private final WebClient client;

    @Override
    public Mono<AuthClient> getByDocument(String document) {
        log.info("Checking if user exists by document number {}", document);
        return client
                .get()
                .uri("/api/v1/usuarios/{document}", document)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response -> {
                            int status = response.statusCode().value();
                            if (status == 404) {
                                log.warn("Usuario no encontrado, document={}", document);
                                return response.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(new RemoteException("Usuario no encontrado")));
                            }
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RemoteException("Error inesperado: " + body)));
                        }
                )
                .bodyToMono(AuthClient.class)
                .doOnSuccess(u -> log.info("✅ AUTH OK: {}", u != null ? u.getData() : "null"));

    }
}
