package co.com.solicitudes.usecase.decideloan;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import co.com.solicitudes.model.loanstatus.gateways.LoanStatusRepository;
import co.com.solicitudes.model.loandecisionpublisher.gateways.LoanDecisionPublisher;
import constant.LoanStatusCode;
import exceptions.LoanApplicationNotFoundException;
import exceptions.LoanStatusNotFoundException;
import exceptions.ValidStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static constant.LoanStatusCode.APROBADA;
import static constant.LoanStatusCode.RECHAZADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecideLoanUseCaseTest {

    @Mock private LoanApplicationRepository applicationRepository;
    @Mock private LoanStatusRepository statusRepository;
    @Mock private LoanDecisionPublisher publisher;

    @InjectMocks
    private DecideLoanUseCase useCase;

    private UUID LOAN_ID;
    private LoanStatus statusAprobada;
    private LoanStatus statusRechazada;
    private LoanStatus statusPendiente;

    @BeforeEach
    void setUp() {
        LOAN_ID = UUID.randomUUID();

        statusAprobada = LoanStatus.builder()
                .id(APROBADA.getCode())
                .name("APROBADA")
                .description("Solicitud aprobada")
                .build();

        statusRechazada = LoanStatus.builder()
                .id(RECHAZADA.getCode())
                .name("RECHAZADA")
                .description("Solicitud rechazada")
                .build();

        // un estado distinto a aprobada/rechazada para probar transiciones válidas
        statusPendiente = LoanStatus.builder()
                .id(1) // cualquier id que no choque con APROBADA/RECHAZADA
                .name("PENDIENTE")
                .description("Pendiente de revisión")
                .build();
    }

    // ===== Helpers =====

    private LoanApplication buildLoan(UUID id, LoanStatus status) {
        return LoanApplication.builder()
                .id(id)
                .numberDocument("123")
                .email("user@mail.com")
                .fullName("John Doe")
                .amount(new BigDecimal("1000000"))
                .baseSalary(new BigDecimal("2500000"))
                .totalMonthlyDebtApprovedRequests(new BigDecimal("0"))
                .termMonths(12)
                .loanType(null)
                .status(status)
                .createdAt(Instant.now())
                .build();
    }

    // =================== TESTS ===================

    @Test
    @DisplayName("Aprueba: actualiza estado y publica (flujo feliz)")
    void shouldApproveLoan_updateStatusAndPublish() {
        // Arrange
        var current = buildLoan(LOAN_ID, statusPendiente);
        var updated = current.toBuilder().status(statusAprobada).build();

        when(statusRepository.findByName("APROBADA")).thenReturn(Mono.just(statusAprobada));
        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.just(current));
        when(applicationRepository.updateStatus(LOAN_ID, APROBADA.getCode()))
                .thenReturn(Mono.just(updated));
        when(publisher.publish(updated)).thenReturn(Mono.empty());

        // Act
        var result = useCase.execute(LOAN_ID, "Aprobada");

        // Assert
        StepVerifier.create(result)
                .expectNext(updated)
                .verifyComplete();

        InOrder inOrder = inOrder(statusRepository, applicationRepository, publisher);
        inOrder.verify(statusRepository).findByName("APROBADA"); // decisión uppercased
        inOrder.verify(applicationRepository).findById(LOAN_ID);
        inOrder.verify(applicationRepository).updateStatus(LOAN_ID, APROBADA.getCode());
        inOrder.verify(publisher).publish(updated);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Rechaza: actualiza estado y publica (flujo feliz)")
    void shouldRejectLoan_updateStatusAndPublish() {
        // Arrange
        var current = buildLoan(LOAN_ID, statusPendiente);
        var updated = current.toBuilder().status(statusRechazada).build();

        when(statusRepository.findByName("RECHAZADA")).thenReturn(Mono.just(statusRechazada));
        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.just(current));
        when(applicationRepository.updateStatus(LOAN_ID, RECHAZADA.getCode()))
                .thenReturn(Mono.just(updated));
        when(publisher.publish(updated)).thenReturn(Mono.empty());

        // Act
        var result = useCase.execute(LOAN_ID, "Rechazada");

        // Assert
        StepVerifier.create(result)
                .expectNext(updated)
                .verifyComplete();

        InOrder inOrder = inOrder(statusRepository, applicationRepository, publisher);
        inOrder.verify(statusRepository).findByName("RECHAZADA");
        inOrder.verify(applicationRepository).findById(LOAN_ID);
        inOrder.verify(applicationRepository).updateStatus(LOAN_ID, RECHAZADA.getCode());
        inOrder.verify(publisher).publish(updated);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Error: estado no encontrado → LoanStatusNotFoundException y no hay side effects")
    void shouldErrorWhenStatusNotFound() {
        // Arrange
        when(statusRepository.findByName("APROBADA")).thenReturn(Mono.empty());

        // Act
        var result = useCase.execute(LOAN_ID, "Aprobada");

        // Assert
        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(LoanStatusNotFoundException.class);
                    assertThat(ex.getMessage()).contains("Estado no encontrado");
                })
                .verify();

        verify(statusRepository).findByName("APROBADA");
        verifyNoInteractions(applicationRepository, publisher);
    }

    @Test
    @DisplayName("Error: solicitud no encontrada → LoanApplicationNotFoundException sin update/publish")
    void shouldErrorWhenLoanNotFound() {
        // Arrange
        when(statusRepository.findByName("APROBADA")).thenReturn(Mono.just(statusAprobada));
        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.empty());

        // Act
        var result = useCase.execute(LOAN_ID, "Aprobada");

        // Assert
        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(LoanApplicationNotFoundException.class);
                    assertThat(ex.getMessage()).contains("Solicitud no encontrada");
                })
                .verify();

        verify(statusRepository).findByName("APROBADA");
        verify(applicationRepository).findById(LOAN_ID);
        verify(applicationRepository, never()).updateStatus(any(), anyInt());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Error: ya estaba aprobada y piden 'Aprobada' → ValidStatusException (sin update/publish)")
    void shouldErrorWhenAlreadyApproved() {
        // Arrange
        var current = buildLoan(LOAN_ID, statusAprobada);
        when(statusRepository.findByName("APROBADA")).thenReturn(Mono.just(statusAprobada));
        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.just(current));

        // Act
        var result = useCase.execute(LOAN_ID, "Aprobada");

        // Assert
        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(ValidStatusException.class);
                    assertThat(ex.getMessage()).contains("ya fue aprobada");
                })
                .verify();

        verify(applicationRepository, never()).updateStatus(any(), anyInt());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Error: ya estaba rechazada y piden 'Rechazada' → ValidStatusException (sin update/publish)")
    void shouldErrorWhenAlreadyRejected() {
        // Arrange
        var current = buildLoan(LOAN_ID, statusRechazada);
        when(statusRepository.findByName("RECHAZADA")).thenReturn(Mono.just(statusRechazada));
        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.just(current));

        // Act
        var result = useCase.execute(LOAN_ID, "Rechazada");

        // Assert
        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(ValidStatusException.class);
                    assertThat(ex.getMessage()).contains("ya fue Rechazada");
                })
                .verify();

        verify(applicationRepository, never()).updateStatus(any(), anyInt());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Normaliza decisión a MAYÚSCULAS al buscar estado (captura de argumento)")
    void shouldUppercaseDecisionWhenFindingStatus() {
        // Arrange
        when(statusRepository.findByName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return "APROBADA".equals(name) ? Mono.just(statusAprobada) : Mono.empty();
        });
        var current = buildLoan(LOAN_ID, statusPendiente);
        var updated = current.toBuilder().status(statusAprobada).build();

        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.just(current));
        when(applicationRepository.updateStatus(eq(LOAN_ID), eq(APROBADA.getCode())))
                .thenReturn(Mono.just(updated));
        when(publisher.publish(updated)).thenReturn(Mono.empty());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        // Act
        var result = useCase.execute(LOAN_ID, "AproBaDa");

        // Assert
        StepVerifier.create(result)
                .expectNext(updated)
                .verifyComplete();

        verify(statusRepository).findByName(captor.capture());
        assertThat(captor.getValue()).isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("Error propagado desde publisher.publish() corta el flujo")
    void shouldPropagatePublisherError() {
        // Arrange
        var current = buildLoan(LOAN_ID, statusPendiente);
        var updated = current.toBuilder().status(statusAprobada).build();

        when(statusRepository.findByName("APROBADA")).thenReturn(Mono.just(statusAprobada));
        when(applicationRepository.findById(LOAN_ID)).thenReturn(Mono.just(current));
        when(applicationRepository.updateStatus(LOAN_ID, APROBADA.getCode()))
                .thenReturn(Mono.just(updated));
        when(publisher.publish(updated)).thenReturn(Mono.error(new RuntimeException("Kafka down")));

        // Act
        var result = useCase.execute(LOAN_ID, "Aprobada");

        // Assert
        StepVerifier.create(result)
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(RuntimeException.class);
                    assertThat(ex.getMessage()).contains("Kafka down");
                })
                .verify();

        InOrder inOrder = inOrder(statusRepository, applicationRepository, publisher);
        inOrder.verify(statusRepository).findByName("APROBADA");
        inOrder.verify(applicationRepository).findById(LOAN_ID);
        inOrder.verify(applicationRepository).updateStatus(LOAN_ID, APROBADA.getCode());
        inOrder.verify(publisher).publish(updated);
        inOrder.verifyNoMoreInteractions();
    }

    // (Opcional) Puedes agregar un test similar al anterior para error en updateStatus()
}
