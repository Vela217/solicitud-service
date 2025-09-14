package co.com.solicitudes.r2dbc;

import co.com.solicitudes.r2dbc.entity.LoanApplicationEntity;
import org.springframework.data.r2dbc.repository.Modifying;
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


    @Query("""
        UPDATE loan_application
        SET loan_status_id = :statusId
        WHERE id = :id
        RETURNING id, number_document, amount, term_months, loan_type_id, loan_status_id,
                  created_at, full_name, email, base_salary, total_monthly_debt_approved_requests
        """)
    Mono<LoanApplicationEntity> updateStatus(@Param("id") UUID id, @Param("statusId") int statusId);

}
