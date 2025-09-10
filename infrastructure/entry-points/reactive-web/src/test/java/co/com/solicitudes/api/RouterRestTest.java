package co.com.solicitudes.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;

class RouterRestTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private Handler handler;

    @MockitoBean
    private RouterFunction<ServerResponse> router;  // SUT
    @BeforeEach
    void setUp() {
        // Arrange común: router + client (sin stubs aún)
        handler = mock(Handler.class);
        router  = new RouterRest().routerFunction(handler);
        webTestClient  = WebTestClient.bindToRouterFunction(router)
                .configureClient()
                .build();
    }
    @Test
    @DisplayName("POST /api/v1/solicitud ⇒ enruta a handler.createLoan y responde 201")
    void post_createsLoan_routesToHandler_andReturns201() {
        // Arrange
        when(handler.createLoan(any()))
                .thenReturn(ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"ok\":true}"));

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/solicitud")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"dummy\":\"payload\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        verify(handler).createLoan(any());
        verifyNoMoreInteractions(handler);
    }

    @Test
    @DisplayName("GET /api/v1/solicitud ⇒ 404 (no hay ruta GET definida)")
    void get_onSamePath_isNotFound() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/sxxxx")
                .exchange()
                .expectStatus().isNotFound();

        verifyNoInteractions(handler);
    }

    @Test
    @DisplayName("POST /api/v1/otra ⇒ 404 (path no mapea)")
    void post_onDifferentPath_isNotFound() {
        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/otra")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNotFound();

        verifyNoInteractions(handler);
    }

    @Test
    @DisplayName("GET /api/v1/solicitud ⇒ enruta a handler.list y responde 200")
    void get_list_routesToHandler_andReturns200() {
        // Arrange
        when(handler.list(any()))
                .thenReturn(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"listado\":true}"));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/solicitud?page=0&size=5")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON);

        verify(handler).list(any());
        verifyNoMoreInteractions(handler);
    }
}
