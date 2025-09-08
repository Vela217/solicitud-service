package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.api.dto.ResponseCreateLoan;
import co.com.solicitudes.api.exception.DtoValidator;
import co.com.solicitudes.api.mapper.LoanRequestMapper;
import co.com.solicitudes.api.mapper.LoanResponseMapper;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;



@ExtendWith(MockitoExtension.class)
class HandlerTest {

    @Mock RegisterLoanApplicationUseCase useCase;
    @Mock TransactionalOperator tx;
    @Mock DtoValidator validator;
    @Mock LoanRequestMapper mapper;
    @Mock LoanResponseMapper responseMapper;

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
        var handler = new Handler(useCase, tx, validator, mapper, responseMapper);
        router = route(POST("/api/v1/solicitud"), handler::createLoan);
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
}
