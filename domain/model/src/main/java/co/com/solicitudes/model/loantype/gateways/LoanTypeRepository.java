package co.com.solicitudes.model.loantype.gateways;

import co.com.solicitudes.model.loantype.LoanType;
import reactor.core.publisher.Mono;

public interface LoanTypeRepository {
    Mono<LoanType> findById(Integer id);
}
