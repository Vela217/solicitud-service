package co.com.solicitudes.r2dbc;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import co.com.solicitudes.r2dbc.entity.LoanApplicationEntity;
import co.com.solicitudes.r2dbc.entity.LoanStatusEntity;
import co.com.solicitudes.r2dbc.entity.LoanTypeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.reactivecommons.utils.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LoanApplicationReactiveRepositoryAdapter :: save()")
class LoanApplicationReactiveRepositoryAdapterTest {

    @Mock
    LoanApplicationReactiveRepository repository;

    @Mock
    LoanTypeReactiveRepository loanTypeRepository;

    @Mock
    LoanStatusReactiveRepository loanStatusRepository;

    @Mock
    ObjectMapper mapper; // no se usa en el flujo override, pero es requerido por el super()

    @Captor
    ArgumentCaptor<LoanApplicationEntity> entityCaptor;

    LoanApplicationReactiveRepositoryAdapter adapter;

    // Fixtures comunes
    private LoanApplication draft;
    private LoanTypeEntity typeE;
    private LoanStatusEntity statusE;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new LoanApplicationReactiveRepositoryAdapter(
                repository, mapper, loanTypeRepository, loanStatusRepository
        );

        draft = LoanApplication.builder()
                .id(null)
                .numberDocument("12345678")
                .amount(new BigDecimal("1200000"))
                .termMonths(12)
                .loanType(LoanType.builder().id(1).build())  // el adapter debería cargar los datos completos
                .status(LoanStatus.builder().id(1).build())  // idem
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        typeE = new LoanTypeEntity();
        typeE.setId(1);
        typeE.setName("Personal");
        typeE.setMinimumAmount(new BigDecimal("500000"));
        typeE.setMaximumAmount(new BigDecimal("20000000"));
        typeE.setInterestRate(12.5f);
        typeE.setAutomaticValidation(true);

        statusE = new LoanStatusEntity();
        statusE.setId(1);
        statusE.setName("Pendiente de revisión");
        statusE.setDescription("En revisión");
    }

    @Test
    @DisplayName("Guarda y enriquece con LoanType + LoanStatus existentes")
    void save_enriched_ok() {
        // Arrange
        when(repository.save(any(LoanApplicationEntity.class))).thenAnswer(inv -> {
            LoanApplicationEntity in = inv.getArgument(0);

            // opcional: afirmar aquí que el que entra no trae id
            assertThat(in.getId()).isNull();

            // simular DB: devolver una NUEVA instancia con id asignado
            LoanApplicationEntity persisted = new LoanApplicationEntity();
            persisted.setId(UUID.randomUUID());
            persisted.setNumberDocument(in.getNumberDocument());
            persisted.setAmount(in.getAmount());
            persisted.setTermMonths(in.getTermMonths());
            persisted.setCreatedAt(in.getCreatedAt());
            persisted.setLoanTypeId(in.getLoanTypeId());
            persisted.setLoanStatusId(in.getLoanStatusId());
            return Mono.just(persisted);
        });
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeE));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.just(statusE));

        // Act
        Mono<LoanApplication> mono = adapter.save(draft);

        // Assert
        StepVerifier.create(mono)
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getNumberDocument()).isEqualTo("12345678");
                    assertThat(saved.getAmount()).isEqualByComparingTo("1200000");
                    assertThat(saved.getTermMonths()).isEqualTo(12);
                    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));

                    // Enriquecimiento
                    assertThat(saved.getLoanType()).satisfies(t -> {
                        assertThat(t.getId()).isEqualTo(1);
                        assertThat(t.getName()).isEqualTo("Personal");
                        assertThat(t.getMinimumAmount()).isEqualByComparingTo("500000");
                        assertThat(t.getMaximumAmount()).isEqualByComparingTo("20000000");
                        assertThat(t.getInterestRate()).isEqualTo(12.5f);
                        assertThat(t.getAutomaticValidation()).isTrue();
                    });
                    assertThat(saved.getStatus()).satisfies(s -> {
                        assertThat(s.getId()).isEqualTo(1);
                        assertThat(s.getName()).isEqualTo("Pendiente de revisión");
                        assertThat(s.getDescription()).isEqualTo("En revisión");
                    });
                })
                .verifyComplete();

        verify(repository).save(entityCaptor.capture());
        LoanApplicationEntity toPersist = entityCaptor.getValue();
        assertThat(toPersist.getId()).isNull(); // lo asigna la “DB”
        assertThat(toPersist.getNumberDocument()).isEqualTo("12345678");
        assertThat(toPersist.getAmount()).isEqualByComparingTo("1200000");
        assertThat(toPersist.getTermMonths()).isEqualTo(12);
        assertThat(toPersist.getCreatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(toPersist.getLoanTypeId()).isEqualTo(1);
        assertThat(toPersist.getLoanStatusId()).isEqualTo(1);

        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoMoreInteractions(repository, loanTypeRepository, loanStatusRepository);
    }

    @Test
    @DisplayName("LoanType no existe ⇒ usa solo el id como fallback (LoanStatus sí existe)")
    void save_typeMissing_fallbackById() {
        // Arrange
        when(repository.save(any(LoanApplicationEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.empty());   // NO existe
        when(loanStatusRepository.findById(1)).thenReturn(Mono.just(statusE));

        // Act
        Mono<LoanApplication> mono = adapter.save(draft);

        // Assert
        StepVerifier.create(mono)
                .assertNext(saved -> {
                    assertThat(saved.getLoanType()).satisfies(t -> {
                        // viene del fallback: solo id, resto null
                        assertThat(t.getId()).isEqualTo(1);
                        assertThat(t.getName()).isNull();
                        assertThat(t.getMinimumAmount()).isNull();
                        assertThat(t.getMaximumAmount()).isNull();
                        assertThat(t.getInterestRate()).isNull();
                        assertThat(t.getAutomaticValidation()).isNull();
                    });
                    assertThat(saved.getStatus()).satisfies(s -> {
                        assertThat(s.getId()).isEqualTo(1);
                        assertThat(s.getName()).isEqualTo("Pendiente de revisión");
                        assertThat(s.getDescription()).isEqualTo("En revisión");
                    });
                })
                .verifyComplete();

        verify(repository).save(any(LoanApplicationEntity.class));
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoMoreInteractions(repository, loanTypeRepository, loanStatusRepository);
    }

    @Test
    @DisplayName("LoanStatus no existe ⇒ usa solo el id como fallback (LoanType sí existe)")
    void save_statusMissing_fallbackById() {
        // Arrange
        when(repository.save(any(LoanApplicationEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeE));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.empty()); // NO existe

        // Act
        Mono<LoanApplication> mono = adapter.save(draft);

        // Assert
        StepVerifier.create(mono)
                .assertNext(saved -> {
                    assertThat(saved.getLoanType()).isNotNull();
                    assertThat(saved.getStatus()).satisfies(s -> {
                        // fallback: solo id
                        assertThat(s.getId()).isEqualTo(1);
                        assertThat(s.getName()).isNull();
                        assertThat(s.getDescription()).isNull();
                    });
                })
                .verifyComplete();

        verify(repository).save(any(LoanApplicationEntity.class));
        verify(loanTypeRepository).findById(1);
        verify(loanStatusRepository).findById(1);
        verifyNoMoreInteractions(repository, loanTypeRepository, loanStatusRepository);
    }

    // ==================== 5) findForReview() y countForReview() ====================

    @Test
    @DisplayName("findForReview(): calcula offset y delega con array correcto; enriquece")
    void findForReview_delegatesWithOffsetAndMaps() {
        // page=2, size=10 => offset=20
        int page = 2, size = 10;
        Integer[] statuses = {1, 2};

        LoanApplicationEntity e1 = new LoanApplicationEntity();
        e1.setId(UUID.randomUUID());
        e1.setNumberDocument("A");
        e1.setAmount(new BigDecimal("1"));
        e1.setTermMonths(1);
        e1.setCreatedAt(Instant.now());
        e1.setLoanTypeId(1);
        e1.setLoanStatusId(1);

        LoanApplicationEntity e2 = new LoanApplicationEntity();
        e2.setId(UUID.randomUUID());
        e2.setNumberDocument("B");
        e2.setAmount(new BigDecimal("2"));
        e2.setTermMonths(2);
        e2.setCreatedAt(Instant.now());
        e2.setLoanTypeId(1);
        e2.setLoanStatusId(1);

        when(repository.findForReview(any(Integer[].class), eq(size), eq(20L)))
                .thenReturn(Flux.just(e1, e2));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeE));
        when(loanStatusRepository.findById(1)).thenReturn(Mono.just(statusE));

        StepVerifier.create(adapter.findForReview(Arrays.asList(statuses), page, size).collectList())
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).getNumberDocument()).isEqualTo("A");
                    assertThat(list.get(1).getNumberDocument()).isEqualTo("B");
                    assertThat(list.get(0).getLoanType().getName()).isEqualTo("Personal");
                })
                .verifyComplete();

        ArgumentCaptor<Integer[]> captor = ArgumentCaptor.forClass(Integer[].class);
        verify(repository).findForReview(captor.capture(), eq(size), eq(20L));
        assertThat(captor.getValue()).containsExactly(1, 2);
    }

    @Test
    @DisplayName("countForReview(): delega correctamente")
    void countForReview_delegates() {
        when(repository.countForReview(any(Integer[].class)))
                .thenReturn(Mono.just(42L));

        StepVerifier.create(adapter.countForReview(Arrays.asList(1, 2)))
                .expectNext(42L)
                .verifyComplete();

        ArgumentCaptor<Integer[]> captor = ArgumentCaptor.forClass(Integer[].class);
        verify(repository).countForReview(captor.capture());
        assertThat(captor.getValue()).containsExactly(1, 2);
    }

// ==================== 6) updateStatus() ====================

    @Test
    @DisplayName("updateStatus(): mapea y enriquece")
    void updateStatus_enriched_ok() {
        UUID id = UUID.randomUUID();
        LoanApplicationEntity e = new LoanApplicationEntity();
        e.setId(id);
        e.setNumberDocument("12345678");
        e.setAmount(new BigDecimal("1200000"));
        e.setTermMonths(12);
        e.setCreatedAt(Instant.now());
        e.setLoanTypeId(1);
        e.setLoanStatusId(2);

        when(repository.updateStatus(id, 2)).thenReturn(Mono.just(e));
        when(loanTypeRepository.findById(1)).thenReturn(Mono.just(typeE));
        LoanStatusEntity status2 = new LoanStatusEntity();
        status2.setId(2); status2.setName("Aprobada"); status2.setDescription("OK");
        when(loanStatusRepository.findById(2)).thenReturn(Mono.just(status2));

        StepVerifier.create(adapter.updateStatus(id, 2))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isEqualTo(id);
                    assertThat(saved.getStatus().getName()).isEqualTo("Aprobada");
                })
                .verifyComplete();
    }

}
