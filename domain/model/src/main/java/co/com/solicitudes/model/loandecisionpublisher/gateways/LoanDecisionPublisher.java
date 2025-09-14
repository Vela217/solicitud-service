package co.com.solicitudes.model.loandecisionpublisher.gateways;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import reactor.core.publisher.Mono;

public interface  LoanDecisionPublisher {
    Mono<String> publish(LoanApplication event);  // retorna el messageId
}
