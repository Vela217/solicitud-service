package co.com.solicitudes.model.authclient.gateways;

import co.com.solicitudes.model.authclient.AuthClient;
import reactor.core.publisher.Mono;

public interface IAuthClient {
    Mono<AuthClient> getByDocument(String numberDocument);
}
