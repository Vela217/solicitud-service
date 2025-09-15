package co.com.solicitudes.model.loanstatus.gateways;

import co.com.solicitudes.model.loanstatus.LoanStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LoanStatusRepository {
    Mono<LoanStatus> findById(Integer id);
    Mono<LoanStatus> findByName(String name);
}
