package co.com.solicitudes.usecase.listforreview;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;


@RequiredArgsConstructor
public class ListForReviewUseCase {

    private final LoanApplicationRepository repo;

    public Mono<PageResult<LoanApplication>> list(int page, int size) {
        var statusIds = java.util.List.of(1, 2, 3);
        return repo.countForReview(statusIds)
                .zipWith(repo.findForReview(statusIds, page, size).collectList())
                .map(t -> new PageResult<>(t.getT2(), t.getT1(), page, size));
    }

    public record PageResult<T>(List<T> content, long total, int page, int size) {
        public int totalPages() {
            return (int) Math.ceil((double) total / size);
        }
    }
}
