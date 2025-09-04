package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.api.dto.GenericResponseDto;
import co.com.solicitudes.api.dto.ResponseCreateLoan;
import co.com.solicitudes.api.exception.DtoValidator;
import co.com.solicitudes.api.mapper.LoanRequestMapper;
import co.com.solicitudes.api.mapper.LoanResponseMapper;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import co.com.solicitudes.usecase.registerloanapplication.RegisterLoanApplicationUseCase;
import exceptions.UserNotFoundException;
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

    @Mock
    RegisterLoanApplicationUseCase useCase;
    @Mock
    TransactionalOperator tx;
    @Mock
    DtoValidator validator;
    @Mock
    LoanRequestMapper mapper;
    @Mock
    LoanResponseMapper responseMapper;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        Handler handler = new Handler(useCase, tx, validator, mapper, responseMapper);
        RouterFunction<ServerResponse> router = route(POST("/api/v1/solicitud"), handler::createLoan);
        client = WebTestClient.bindToRouterFunction(router)
                .configureClient()
                .build();

        when(tx.transactional(Mockito.<Mono<?>>any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("POST /api/v1/solicitud ⇒ 201 Created con envoltura GenericResponseDto")
    void createLoan_shouldReturn201() {
        // ========== Arrange ==========
        CreateLoanRequestDto dto = new CreateLoanRequestDto(
                "12345678",
                12,
                new BigDecimal("1200000"),
                1
        );

        // validator devuelve el mismo DTO (flujo feliz)
        when(validator.validate(any(CreateLoanRequestDto.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // mapper: construimos el modelo desde el dto recibido
        when(mapper.toModel(any(CreateLoanRequestDto.class)))
                .thenAnswer(inv -> {
                    CreateLoanRequestDto d = inv.getArgument(0);
                    return LoanApplication.builder()
                            .numberDocument(d.numberDocument())
                            .termMonths(d.termMonths())
                            .amount(d.amount())
                            .loanType(LoanType.builder().id(d.type()).build())
                            .build();
                });

        // use case: simula persistencia (id + status + createdAt)
        when(useCase.registerLoanApplication(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication in = inv.getArgument(0);
                    return Mono.just(
                            in.toBuilder()
                                    .id(UUID.randomUUID())
                                    .status(LoanStatus.builder().id(1).name("Pendiente de revisión").build())
                                    .createdAt(Instant.now())
                                    .build()
                    );
                });

        // response mapper: crea el DTO de salida desde el modelo guardado
        when(responseMapper.toResponseDto(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication la = inv.getArgument(0);
                    return new ResponseCreateLoan(
                            la.getId(),
                            la.getNumberDocument(),
                            la.getTermMonths(),
                            la.getAmount(),
                            new ResponseCreateLoan.LoanTypeInfo(
                                    la.getLoanType().getId(), "Personal", null, null, 12.5f, true),
                            new ResponseCreateLoan.LoanStatusInfo(
                                    la.getStatus().getId(), la.getStatus().getName(), "En revisión"),
                            la.getCreatedAt()
                    );
                });

        // ============ Act ============
        client.post()
                .uri("/api/v1/solicitud")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dto) // WebTestClient serializa -> handler deserializa
                .exchange()

                // =========== Assert ==========
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                // Envoltura
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Solicitud creada con éxito")
                .jsonPath("$.statusCode").isEqualTo(201)
                // Payload
                .jsonPath("$.data.numberDocument").isEqualTo("12345678")
                .jsonPath("$.data.termMonths").isEqualTo(12)
                .jsonPath("$.data.amount").isEqualTo(1200000)
                .jsonPath("$.data.loanType.id").isEqualTo(1)
                .jsonPath("$.data.status.id").isEqualTo(1)
                .jsonPath("$.data.createdAt").exists();

        // Interacciones (AAA - Assert)
        verify(validator).validate(any(CreateLoanRequestDto.class));
        verify(mapper).toModel(any(CreateLoanRequestDto.class));
        verify(useCase).registerLoanApplication(any(LoanApplication.class));
        verify(responseMapper).toResponseDto(any(LoanApplication.class));
        verify(tx, atLeastOnce()).transactional(Mockito.<Mono<?>>any());
        verifyNoMoreInteractions(validator, mapper, useCase, responseMapper, tx);
    }
}