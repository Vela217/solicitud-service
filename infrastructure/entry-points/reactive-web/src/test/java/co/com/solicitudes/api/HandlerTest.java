package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.api.dto.ResponseCreateLoan;
import co.com.solicitudes.api.exception.DtoValidator;
import co.com.solicitudes.api.mapper.LoanRequestMapper;
import co.com.solicitudes.api.mapper.LoanResponseMapper;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import co.com.solicitudes.usecase.decideloan.DecideLoanUseCase;
import co.com.solicitudes.usecase.listforreview.ListForReviewUseCase;
import co.com.solicitudes.usecase.registerloanapplication.RegisterLoanApplicationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import co.com.solicitudes.api.dto.DecisionRequest;


@ExtendWith(MockitoExtension.class)
class HandlerTest {

    @Mock RegisterLoanApplicationUseCase useCase;
    @Mock ListForReviewUseCase listForReviewUseCase;
    @Mock TransactionalOperator tx;
    @Mock DtoValidator validator;
    @Mock LoanRequestMapper mapper;
    @Mock LoanResponseMapper responseMapper;
    @Mock private DecideLoanUseCase decideLoanUseCase;

    private RouterFunction<ServerResponse> router;

    // Helper para crear un WebTestClient con JWT en el SecurityContext
    private WebTestClient clientWithJwt(String numberDocument, String... roles) {
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("numberDocument", numberDocument)
                .build();

        var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                jwt,
                java.util.Arrays.stream(roles)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList()
        );

        org.springframework.web.server.WebFilter injectCtx = (ex, chain) -> {
            var ctx = new org.springframework.security.core.context.SecurityContextImpl(auth);
            return chain.filter(ex).contextWrite(
                    org.springframework.security.core.context.ReactiveSecurityContextHolder.withSecurityContext(
                            reactor.core.publisher.Mono.just(ctx)));
        };

        return WebTestClient.bindToRouterFunction(router)
                .webFilter(injectCtx)
                .configureClient()
                .build();
    }

    @BeforeEach
    void setUp() {
        var handler = new Handler(useCase, tx, validator, mapper, responseMapper,listForReviewUseCase, decideLoanUseCase);
        router = route(POST("/api/v1/solicitud"), handler::createLoan)
                .andRoute(GET("/api/v1/solicitud"), handler::list)
                .andRoute(PUT("/api/v1/solicitud"), handler::decide);

    }

    @Test
    @DisplayName("POST /api/v1/solicitud ⇒ 201 CREATED usando numberDocument del token")
    void createLoan_shouldReturn201_withJwtClaimAsSourceOfTruth() {
        var client = clientWithJwt("12345678", "ROLE_CLIENTE");

        // Body SIN documento: el handler usará el del token
        var body = new CreateLoanRequestDto(null, 12, new BigDecimal("1200000"), 1);

        when(validator.validate(any(CreateLoanRequestDto.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        when(mapper.toModel(any(CreateLoanRequestDto.class))).thenAnswer(inv -> {
            var d = (CreateLoanRequestDto) inv.getArgument(0);
            return LoanApplication.builder()
                    .numberDocument(d.numberDocument())
                    .termMonths(d.termMonths())
                    .amount(d.amount())
                    .loanType(LoanType.builder().id(d.type()).build())
                    .build();
        });

        when(useCase.registerLoanApplication(any(LoanApplication.class))).thenAnswer(inv -> {
            var in = (LoanApplication) inv.getArgument(0);
            return Mono.just(in.toBuilder()
                    .id(UUID.randomUUID())
                    .status(LoanStatus.builder().id(1).name("Pendiente de revisión").build())
                    .createdAt(Instant.now())
                    .build());
        });

        when(responseMapper.toResponseDto(any(LoanApplication.class))).thenAnswer(inv -> {
            var la = (LoanApplication) inv.getArgument(0);
            return new ResponseCreateLoan(
                    la.getId(), "12345678", la.getTermMonths(), la.getAmount(),
                    new ResponseCreateLoan.LoanTypeInfo(1, "Personal", null, null, 12.5f, true),
                    new ResponseCreateLoan.LoanStatusInfo(1, "Pendiente de revisión", "En revisión"),
                    la.getCreatedAt());
        });

        // ⬇️ Solo este test necesita el transactional
        when(tx.transactional(Mockito.<Mono<?>>any()))
                .thenAnswer(inv -> inv.getArgument(0));

        client.post()
                .uri("/api/v1/solicitud")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.statusCode").isEqualTo(201)
                .jsonPath("$.data.numberDocument").isEqualTo("12345678");

        verify(validator).validate(any(CreateLoanRequestDto.class));
        verify(mapper).toModel(any(CreateLoanRequestDto.class));
        verify(useCase).registerLoanApplication(any(LoanApplication.class));
        verify(responseMapper).toResponseDto(any(LoanApplication.class));
        verify(tx).transactional(Mockito.<Mono<?>>any());
        verifyNoMoreInteractions(validator, mapper, useCase, responseMapper, tx);
    }

    @Test
    @DisplayName("POST /api/v1/solicitud ⇒ 403 si numberDocument del body != del token")
    void createLoan_shouldReturn403_whenBodyDocDiffersFromToken() {
        var client = clientWithJwt("12345678", "ROLE_CLIENTE");
        var body = new CreateLoanRequestDto("99999999", 12, new BigDecimal("1200000"), 1);

        when(validator.validate(any(CreateLoanRequestDto.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        client.post()
                .uri("/api/v1/solicitud")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.statusCode").isEqualTo(403)
                .jsonPath("$.message").isEqualTo("No puedes crear solicitudes para otro usuario");

        verify(validator).validate(any(CreateLoanRequestDto.class));
        verifyNoInteractions(mapper, useCase, responseMapper, tx);
    }

    @Test
    @DisplayName("GET /api/v1/solicitud/list ⇒ 200 OK con PageResult")
    void listLoans_shouldReturn200_withPageResult() {
        var client = clientWithJwt("12345678", "ROLE_ASESOR");

        var loanApp = LoanApplication.builder()
                .id(UUID.randomUUID())
                .numberDocument("12345678")
                .termMonths(12)
                .amount(new BigDecimal("1500000"))
                .build();

        // PageResult esperado
        var pageResult = new ListForReviewUseCase.PageResult<>(
                List.of(loanApp), // content
                1L,               // total
                0,                // page
                10                // size
        );

        when(listForReviewUseCase.list(eq(0), eq(10)))
                .thenReturn(Mono.just(pageResult));

        client.get()
                .uri("/api/v1/solicitud?page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.statusCode").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("Listado generado")
                .jsonPath("$.data.content[0].numberDocument").isEqualTo("12345678")
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.page").isEqualTo(0)
                .jsonPath("$.data.size").isEqualTo(10);

        verify(listForReviewUseCase).list(eq(0), eq(10));
        verifyNoMoreInteractions(listForReviewUseCase);
    }


    @Test
    @DisplayName("POST /api/v1/solicitud/decide ⇒ 200 OK, aplica tx y retorna GenericResponseDto con LoanApplication actualizado")
    void decide_shouldReturn200_andApplyTransaction() {
        // Arrange
        UUID id = UUID.randomUUID();
        String decision = "Aprobada";

        var current = LoanApplication.builder()
                .id(id)
                .numberDocument("12345678")
                .termMonths(12)
                .amount(new BigDecimal("1500000"))
                .status(LoanStatus.builder().id(1).name("PENDIENTE").build())
                .createdAt(Instant.now())
                .build();

        var updated = current.toBuilder()
                .status(LoanStatus.builder().id(2).name("APROBADA").build())
                .build();

        when(decideLoanUseCase.execute(eq(id), eq(decision))).thenReturn(Mono.just(updated));

        // El handler usa .as(tx::transactional) ⇒ devolvemos el mismo Publisher
        when(tx.transactional(Mockito.<Mono<?>>any())).thenAnswer(inv -> inv.getArgument(0));

        // Act + Assert
        WebTestClient.bindToRouterFunction(router)
                .build()
                .put()
                .uri("/api/v1/solicitud")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DecisionRequest(decision, id))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.statusCode").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("Solicitud actualizada")
                .jsonPath("$.data.id").isEqualTo(id.toString())
                .jsonPath("$.data.status.id").isEqualTo(2)
                .jsonPath("$.data.status.name").isEqualTo("APROBADA");

        // Verificaciones
        verify(decideLoanUseCase).execute(eq(id), eq(decision));
        verify(tx).transactional(Mockito.<Mono<?>>any());
        verifyNoMoreInteractions(decideLoanUseCase, tx);
    }


}
