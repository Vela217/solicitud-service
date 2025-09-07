package co.com.solicitudes.consumer;


import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import java.io.IOException;
import java.rmi.RemoteException;
import static org.assertj.core.api.Assertions.assertThat;

class RestConsumerTest {

    private RestConsumer restConsumer;
    private MockWebServer mockBackEnd;

    @BeforeEach
    void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockBackEnd.url("/").toString())
                .build();
        restConsumer = new RestConsumer(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @Test
    @DisplayName("200 OK ⇒ deserializa AuthClient y devuelve éxito")
    void getByDocument_ok() throws Exception {
        mockBackEnd.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                    {"statusCode":200,"success":true,"message":"ok","data":{"any":"thing"}}
                """));

        StepVerifier.create(restConsumer.getByDocument("12345678"))
                .assertNext(a -> {
                    assertThat(a.getStatusCode()).isEqualTo(200);
                    assertThat(a.getSuccess()).isTrue();
                    assertThat(a.getMessage()).isEqualTo("ok");
                    assertThat(a.getData()).isNotNull();
                })
                .verifyComplete();

        var req = mockBackEnd.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/v1/usuarios/12345678");
    }

    @Test
    @DisplayName("404 Not Found ⇒ lanza RemoteException('Usuario no encontrado')")
    void getByDocument_404() throws Exception {
        mockBackEnd.enqueue(new MockResponse()
                .setResponseCode(HttpStatus.NOT_FOUND.value())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .setBody("usuario no existe"));

        StepVerifier.create(restConsumer.getByDocument("00000000"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(RemoteException.class);
                    assertThat(ex).hasMessage("Usuario no encontrado");
                })
                .verify();

        var req = mockBackEnd.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/v1/usuarios/00000000");
    }

    @Test
    @DisplayName("4xx distinto de 404 ⇒ lanza RemoteException con el cuerpo en el mensaje")
    void getByDocument_other4xx() throws Exception {
        // Arrange: simulamos 409 CONFLICT con un cuerpo cualquiera
        mockBackEnd.enqueue(new MockResponse()
                .setResponseCode(409) // <- 4xx que NO es 404
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"error\":\"email ya en uso\"}"));

        // Act
        var mono = restConsumer.getByDocument("99999999");

        // Assert
        StepVerifier.create(mono)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(RemoteException.class);
                    // el mensaje viene de: "Error inesperado: " + body
                    assertThat(ex.getMessage()).contains("Error inesperado:");
                    assertThat(ex.getMessage()).contains("email ya en uso");
                })
                .verify();

        // comprobamos que llamó a la ruta correcta
        var req = mockBackEnd.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/v1/usuarios/99999999");
    }
}
