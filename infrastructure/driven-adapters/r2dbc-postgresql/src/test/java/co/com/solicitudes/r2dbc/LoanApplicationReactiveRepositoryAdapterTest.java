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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
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
}
