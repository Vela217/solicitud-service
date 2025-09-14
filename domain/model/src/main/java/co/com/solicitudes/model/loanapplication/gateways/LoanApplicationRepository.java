package co.com.solicitudes.model.loanapplication.gateways;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanApplicationRepository {
Mono<LoanApplication> save(LoanApplication loanApplication);
    Flux<LoanApplication> findForReview(Collection<Integer> statuses, int page, int size);
    Mono<Long> countForReview(List<Integer> statusIds);
    Mono<LoanApplication> findById(UUID loanId);
    Mono<LoanApplication> updateStatus(UUID id, int statusId);

}

