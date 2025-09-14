package co.com.solicitudes.usecase.decideloan;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import co.com.solicitudes.model.loandecisionpublisher.gateways.LoanDecisionPublisher;
import co.com.solicitudes.model.loanstatus.gateways.LoanStatusRepository;
import constant.LoanStatusCode;
import exceptions.LoanApplicationNotFoundException;
import exceptions.LoanStatusNotFoundException;
import exceptions.ValidStatusException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static constant.LoanStatusCode.RECHAZADA;
import static constant.LoanStatusCode.APROBADA;

@RequiredArgsConstructor
public class DecideLoanUseCase {

    private final LoanApplicationRepository repository;
    private final  LoanStatusRepository repositoryType;
    private final LoanDecisionPublisher publisher;


    public Mono<LoanApplication> execute(UUID loanId, String decision) {
        return repositoryType.findByName(decision)
                .switchIfEmpty(Mono.error(new LoanStatusNotFoundException("Estado no encontrado", 400)))
                .zipWhen(s -> repository.findById(loanId)
                        .switchIfEmpty(Mono.error(new LoanApplicationNotFoundException("Solicitud no encontrada", 400))))
                .flatMap(tuple -> {
                    var status = tuple.getT1();
                    var current = tuple.getT2();

                    if (current.getStatus().getId() == APROBADA.getCode() && decision.equalsIgnoreCase("Aprobada")){
                        return  Mono.error(new ValidStatusException("La solicitud ya fue aprobada", 400));
                    }
                    else  if (current.getStatus().getId() == RECHAZADA.getCode() && decision.equalsIgnoreCase("Rechazada")){
                        return  Mono.error(new ValidStatusException("La solicitud ya fue Rechazada", 400));
                    }

                    return repository.updateStatus(current.getId(), status.getId())
                            .flatMap(updated -> publisher.publish(updated).thenReturn(updated));
                });
    }

}
