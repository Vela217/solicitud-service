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
import exceptions.LoanTypeNotFoundException;
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
import java.util.Map;
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
                .loanType(LoanType.builder().id(1).build())
                .status(null)
                .createdAt(null)
                .build();
    }

    private AuthClient authSuccessWithData(java.util.Map<String, ?> data) {
        return AuthClient.builder().success(true).data(data).build();
    }

    @Test
    @DisplayName("OK ⇒ enriquece con datos de AuthClient, valida montos y guarda con id")
    void registerLoanApplication_ok_enriched() {
        // Arrange
        var authData = Map.of(
                "numberDocument", "12345678", // mismo que draft
                "email", "cliente@email.com",
                "name", "Cliente",
                "lastName", "Tovar",
                "baseSalary", "2000000" // puede venir como String/Number
        );

        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(authSuccessWithData(authData)));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.just(pending));
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication arg = inv.getArgument(0);
                    return Mono.just(arg.toBuilder()
                            .id(UUID.randomUUID())
                            .createdAt(arg.getCreatedAt() != null ? arg.getCreatedAt() : Instant.now())
                            .build());
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

                    // enriquecidos
                    assertThat(saved.getNumberDocument()).isEqualTo("12345678");
                    assertThat(saved.getEmail()).isEqualTo("cliente@email.com");
                    assertThat(saved.getFullName()).isEqualTo("Cliente Tovar");
                    assertThat(saved.getBaseSalary()).isEqualByComparingTo("2000000");

                    // monto y deuda mensual aprobada fija del caso de uso
                    assertThat(saved.getAmount()).isEqualByComparingTo("1200000");
                    assertThat(saved.getTotalMonthlyDebtApprovedRequests())
                            .isEqualByComparingTo("1200000");
                })
                .verifyComplete();

        verify(loanApplicationRepository).save(loanCaptor.capture());
        var toPersist = loanCaptor.getValue();
        assertThat(toPersist.getCreatedAt()).isNotNull();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoMoreInteractions(authClient, loanTypeRepository, loanStatusRepository, loanApplicationRepository);
    }

    @Test
    @DisplayName("Auth devuelve numberDocument distinto ⇒ sobreescribe el del draft antes de guardar")
    void registerLoanApplication_overridesNumberDocument_fromAuth() {
        // Arrange
        var draft = draftOk.toBuilder().numberDocument("00000000").build();
        var authData = Map.of("numberDocument", "99999999");

        when(authClient.getByDocument("00000000"))
                .thenReturn(Mono.just(authSuccessWithData(authData)));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.just(pending));
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> Mono.just(((LoanApplication) inv.getArgument(0))
                        .toBuilder().id(UUID.randomUUID()).build()));

        // Act
        Mono<LoanApplication> result = useCase.registerLoanApplication(draft);

        // Assert
        StepVerifier.create(result)
                .assertNext(saved -> assertThat(saved.getNumberDocument()).isEqualTo("99999999"))
                .verifyComplete();

        verify(authClient).getByDocument("00000000");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verify(loanApplicationRepository).save(any(LoanApplication.class));
        verifyNoMoreInteractions(authClient, loanTypeRepository, loanStatusRepository, loanApplicationRepository);
    }

    @Test
    @DisplayName("Usuario no existe ⇒ UserNotFoundException (no consulta tipo/estado ni guarda)")
    void registerLoanApplication_userNotFound() {
        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(AuthClient.builder().success(false).build()));

        StepVerifier.create(useCase.registerLoanApplication(draftOk))
                .expectErrorSatisfies(ex -> assertThat(ex)
                        .isInstanceOf(UserNotFoundException.class)
                        .hasMessage("Usuario no encontrado"))
                .verify();

        verify(authClient).getByDocument("12345678");
        verifyNoInteractions(loanApplicationRepository);
    }

    @Test
    @DisplayName("LoanType no existe ⇒ LoanTypeNotFoundException")
    void registerLoanApplication_loanTypeNotFound() {
        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(authSuccessWithData(Map.of())));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.registerLoanApplication(draftOk))
                .expectError(LoanTypeNotFoundException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
// zip suscribe también estado:
        verify(loanStatusRepository, atLeastOnce()).findById(1);
// no se guarda
        verifyNoInteractions(loanApplicationRepository);
        verifyNoMoreInteractions(authClient, loanTypeRepository, loanStatusRepository);
    }

    @Test
    @DisplayName("Estado PENDIENTE no existe ⇒ LoanStatusNotFoundException, no se guarda")
    void registerLoanApplication_statusNotFound() {
        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(authSuccessWithData(Map.of())));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.registerLoanApplication(draftOk))
                .expectError(LoanStatusNotFoundException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoInteractions(loanApplicationRepository);
        verifyNoMoreInteractions(authClient, loanTypeRepository, loanStatusRepository);
    }

    @Test
    @DisplayName("Monto < mínimo ⇒ AmountException (no consulta estado ni guarda)")
    void registerLoanApplication_amountBelowMin() {
        var draft = draftOk.toBuilder().amount(new BigDecimal("100000")).build();

        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(authSuccessWithData(Map.of())));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));

        StepVerifier.create(useCase.registerLoanApplication(draft))
                .expectError(AmountException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository, atLeastOnce()).findById(1);
        verifyNoMoreInteractions(authClient, loanTypeRepository);
    }

    @Test
    @DisplayName("Monto > máximo ⇒ AmountException (no consulta estado ni guarda)")
    void registerLoanApplication_amountAboveMax() {
        var draft = draftOk.toBuilder().amount(new BigDecimal("500000000")).build();

        when(authClient.getByDocument("12345678"))
                .thenReturn(Mono.just(authSuccessWithData(Map.of())));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeOk));

        StepVerifier.create(useCase.registerLoanApplication(draft))
                .expectError(AmountException.class)
                .verify();

        verify(authClient).getByDocument("12345678");
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository, atLeastOnce()).findById(1);
        verifyNoInteractions(loanApplicationRepository);
    }
}
