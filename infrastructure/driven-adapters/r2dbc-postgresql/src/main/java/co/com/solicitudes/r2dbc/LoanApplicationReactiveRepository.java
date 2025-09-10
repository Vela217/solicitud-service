package co.com.solicitudes.r2dbc;

import co.com.solicitudes.r2dbc.entity.LoanApplicationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

// TODO: This file is just an example, you should delete or modify it
public interface LoanApplicationReactiveRepository extends ReactiveCrudRepository<LoanApplicationEntity, UUID>, ReactiveQueryByExampleExecutor<LoanApplicationEntity> {
 //filas maximas

    @Query("""
        SELECT * FROM
        loan_application
        WHERE loan_status_id = ANY(:statusIds)
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<LoanApplicationEntity> findForReview(@Param("statusIds") Integer[] statusIds,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    @Query("""
        SELECT COUNT(*)
        FROM loan_application
        WHERE loan_status_id = ANY(:statusIds)
        """)
    Mono<Long> countForReview(@Param("statusIds") Integer[] statusIds);
}
