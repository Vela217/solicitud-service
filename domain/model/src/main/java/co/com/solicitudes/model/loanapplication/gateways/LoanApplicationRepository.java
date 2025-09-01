package co.com.solicitudes.model.loanapplication.gateways;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import reactor.core.publisher.Mono;

public interface LoanApplicationRepository {
Mono<LoanApplication> save(LoanApplication loanApplication);
}

