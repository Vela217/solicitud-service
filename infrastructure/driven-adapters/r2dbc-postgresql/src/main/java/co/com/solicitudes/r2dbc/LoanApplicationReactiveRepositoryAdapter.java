package co.com.solicitudes.r2dbc;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import co.com.solicitudes.r2dbc.entity.LoanApplicationEntity;
import co.com.solicitudes.r2dbc.entity.LoanTypeEntity;
import co.com.solicitudes.r2dbc.entity.LoanStatusEntity;
import co.com.solicitudes.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class LoanApplicationReactiveRepositoryAdapter extends
        ReactiveAdapterOperations<LoanApplication/* change for domain model */, LoanApplicationEntity, UUID, LoanApplicationReactiveRepository>
        implements LoanApplicationRepository {

    private final LoanTypeReactiveRepository loanTypeRepository;
    private final LoanStatusReactiveRepository loanStatusRepository;

    public LoanApplicationReactiveRepositoryAdapter(
            LoanApplicationReactiveRepository repository,
            ObjectMapper mapper,
            LoanTypeReactiveRepository loanTypeRepository,
            LoanStatusReactiveRepository loanStatusRepository) {
        super(repository, mapper, d -> mapper.map(d, LoanApplication.class/* change for domain model */));
        this.loanTypeRepository = loanTypeRepository;
        this.loanStatusRepository = loanStatusRepository;
    }

    @Override
    public Mono<LoanApplication> save(LoanApplication model) {
        LoanApplicationEntity entity = toEntity(model);
        return repository.save(entity)
                .flatMap(this::toModelWithFullData);
    }

    private Mono<LoanApplication> toModelWithFullData(LoanApplicationEntity e) {
        Mono<LoanType> loanTypeMono = e.getLoanTypeId() != null
                ? loanTypeRepository.findById(e.getLoanTypeId())
                .map(this::toLoanTypeModel)
                .switchIfEmpty(Mono.just(LoanType.builder().id(e.getLoanTypeId()).build()))
                : Mono.just(null);

        Mono<LoanStatus> loanStatusMono = e.getLoanStatusId() != null
                ? loanStatusRepository.findById(e.getLoanStatusId())
                .map(this::toLoanStatusModel)
                .switchIfEmpty(Mono.just(LoanStatus.builder().id(e.getLoanStatusId()).build()))
                : Mono.just(null);

        return Mono.zip(loanTypeMono, loanStatusMono)
                .map(tuple -> LoanApplication.builder()
                        .id(e.getId())
                        .numberDocument(e.getNumberDocument())
                        .amount(e.getAmount())
                        .termMonths(e.getTermMonths())
                        .createdAt(e.getCreatedAt())
                        .loanType(tuple.getT1())
                        .status(tuple.getT2())
                        .build());
    }

    private LoanType toLoanTypeModel(LoanTypeEntity entity) {
        return LoanType.builder()
                .id(entity.getId())
                .name(entity.getName())
                .minimumAmount(entity.getMinimumAmount())
                .maximumAmount(entity.getMaximumAmount())
                .interestRate(entity.getInterestRate())
                .automaticValidation(entity.getAutomaticValidation())
                .build();
    }

    private LoanStatus toLoanStatusModel(LoanStatusEntity entity) {
        return LoanStatus.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .build();
    }

    private LoanApplicationEntity toEntity(LoanApplication m) {
        LoanApplicationEntity e = new LoanApplicationEntity();
        e.setId(m.getId());
        e.setNumberDocument(m.getNumberDocument());
        e.setAmount(m.getAmount());
        e.setTermMonths(m.getTermMonths());
        e.setCreatedAt(m.getCreatedAt());
        e.setLoanTypeId(m.getLoanType() != null ? m.getLoanType().getId() : null);
        e.setLoanStatusId(m.getStatus() != null ? m.getStatus().getId() : null);

        return e;
    }

}