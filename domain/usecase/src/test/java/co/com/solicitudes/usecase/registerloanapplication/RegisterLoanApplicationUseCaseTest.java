package co.com.solicitudes.usecase.registerloanapplication;


import co.com.solicitudes.model.authclient.AuthClient;
import co.com.solicitudes.model.authclient.gateways.IAuthClient;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loanstatus.gateways.LoanStatusRepository;
import co.com.solicitudes.model.loantype.LoanType;
import co.com.solicitudes.model.loantype.gateways.LoanTypeRepository;
import exceptions.AmountException;
import exceptions.LoanStatusNotFoundException;
import exceptions.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterLoanApplicationUseCaseTest {

    @Mock IAuthClient authClient;
    @Mock LoanTypeRepository loanTypeRepository;
    @Mock LoanStatusRepository loanStatusRepository;
    @Mock LoanApplicationRepository loanApplicationRepository;

    @InjectMocks
    RegisterLoanApplicationUseCase useCase;

    @Captor ArgumentCaptor<LoanApplication> loanCaptor;

    private LoanType typeOk;
    private LoanStatus pending;
    private LoanApplication draftOk;

    @BeforeEach
    void setUp() {
        // Arrange común: entidades típicas válidas
        typeOk = LoanType.builder()
                .id(1)
                .name("Personal")
                .minimumAmount(new BigDecimal("500000"))
                .maximumAmount(new BigDecimal("20000000"))
                .interestRate(12.5f)
                .automaticValidation(true)
                .build();

        pending = LoanStatus.builder()
                .id(1)
                .name("Pendiente de revisión")
                .description("En revisión")
                .build();

        draftOk = LoanApplication.builder()
                .id(null)
                .numberDocument("12345678")
                .amount(new BigDecimal("1200000"))
                .termMonths(12)
                .loanType(LoanType.builder().id(1).build()) // sólo id; el caso de uso cargará el tipo completo
                .status(null)
                .createdAt(null)
                .build();
    }

    @Test
    @DisplayName("Usuario existe + tipo existe + monto válido + estado existe ⇒ guarda y retorna enriquecido")
    void registerLoanApplication_ok() {
        // Arrange
        when(authClient.getByDocument(eq("12345678")))
                .thenReturn(Mono.just(AuthClient.builder().success(true).build()));
        when(loanTypeRepository.findById(eq(1))).thenReturn(Mono.just(typeOk));
        when(loanStatusRepository.findById(eq(1))).thenReturn(Mono.just(pending));
        // devolvemos lo que nos pasan pero con un id para simular persistencia
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication arg = inv.getArgument(0);
                    return Mono.just(arg.toBuilder().id(UUID.randomUUID()).build());
                });

        // Act
        Mono<LoanApplication> result = useCase.registerLoanApplication(draftOk);

        // Assert
        StepVerifier.create(result)
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getLoanType()).usingRecursiveComparison().isEqualTo(typeOk);
                    assertThat(saved.getStatus()).usingRecursiveComparison().isEqualTo(pending);
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getNumberDocument()).isEqualTo("12345678");
                    assertThat(saved.getAmount()).isEqualByComparingTo("1200000");
                })
                .verifyComplete();

        verify(loanApplicationRepository).save(loanCaptor.capture());
        LoanApplication toPersist = loanCaptor.getValue();
        assertThat(toPersist.getCreatedAt()).isNotNull();
        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoMoreInteractions(authClient, loanTypeRepository, loanStatusRepository, loanApplicationRepository);
    }

    @Test
    @DisplayName("Usuario no existe ⇒ UserNotFoundException, no se consulta ni tipo/estado, no se guarda")
    void registerLoanApplication_userNotFound() {

        String doc = draftOk.getNumberDocument(); // "12345678"
        // Arrange
        when(authClient.getByDocument(doc))
                .thenReturn(Mono.just(AuthClient.builder().success(false).build()));

        // Act
        Mono<LoanApplication> result = useCase.registerLoanApplication(draftOk);

        // Assert
        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> assertThat(ex)
                        .isInstanceOf(UserNotFoundException.class)
                        .hasMessage("Usuario no encontrado"))
                .verify();

        verify(authClient).getByDocument( doc);
        verifyNoInteractions(loanTypeRepository, loanStatusRepository, loanApplicationRepository);
    }


    @Test
    @DisplayName("Estado PENDIENTE no existe ⇒ LoanStatusNotFoundException, no se guarda")
    void registerLoanApplication_statusNotFound() {
        // Arrange
        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(AuthClient.builder().success(true).build()));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.empty());

        // Act
        Mono<LoanApplication> result = useCase.registerLoanApplication(draftOk);

        // Assert
        StepVerifier.create(result)
                .expectError(LoanStatusNotFoundException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoInteractions(loanApplicationRepository);
        verifyNoMoreInteractions(authClient, loanTypeRepository, loanStatusRepository);
    }

    @Test
    @DisplayName("Monto fuera de rango (debajo del mínimo) ⇒ AmountException, no se consulta estado ni se guarda")
    void registerLoanApplication_amountBelowMin() {
        // Arrange
        LoanApplication draft = draftOk.toBuilder().amount(new BigDecimal("100000")).build(); // < 500000
        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(AuthClient.builder().success(true).build()));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));

        // Act
        Mono<LoanApplication> result = useCase.registerLoanApplication(draft);

        // Assert
        StepVerifier.create(result)
                .expectError(AmountException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verifyNoInteractions(loanStatusRepository, loanApplicationRepository);
        verifyNoMoreInteractions(authClient, loanTypeRepository);
    }

    @Test
    @DisplayName("Monto fuera de rango (por encima del máximo) ⇒ AmountException")
    void registerLoanApplication_amountAboveMax() {
        // Arrange
        LoanApplication draft = draftOk.toBuilder().amount(new BigDecimal("500000000")).build(); // > 20000000
        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(AuthClient.builder().success(true).build()));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));

        // Act
        Mono<LoanApplication> result = useCase.registerLoanApplication(draft);

        // Assert
        StepVerifier.create(result)
                .expectError(AmountException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verifyNoInteractions(loanStatusRepository, loanApplicationRepository);
        verifyNoMoreInteractions(authClient, loanTypeRepository);
    }
}