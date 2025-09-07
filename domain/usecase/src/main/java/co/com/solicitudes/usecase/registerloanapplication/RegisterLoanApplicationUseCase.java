package co.com.solicitudes.usecase.registerloanapplication;

import co.com.solicitudes.model.authclient.AuthClient;
import co.com.solicitudes.model.authclient.gateways.IAuthClient;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanapplication.gateways.LoanApplicationRepository;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loanstatus.gateways.LoanStatusRepository;
import co.com.solicitudes.model.loantype.LoanType;
import co.com.solicitudes.model.loantype.gateways.LoanTypeRepository;
import exceptions.AmountException;
import exceptions.LoanStatusNotFoundException;
import exceptions.LoanTypeNotFoundException;
import exceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RequiredArgsConstructor
public class RegisterLoanApplicationUseCase {

    private final IAuthClient authClient;
    private final LoanTypeRepository loanTypeRepository;
    private final LoanStatusRepository loanStatusRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    private static final int PENDING_REVIEW_ID = 1;

    public Mono<LoanApplication> registerLoanApplication(LoanApplication draft) {
        return verifyUserByDocumentNumber(draft)
                .then(findLoanTypeById(draft))
                .flatMap(type -> validAmount(draft, type).thenReturn(type))
                .zipWhen(type -> findPendingReviewLoanStatus())
                .flatMap(tuple -> {
                    LoanType type   = tuple.getT1();
                    LoanStatus stat = tuple.getT2();
                    LoanApplication toSave = draft.toBuilder()
                            .loanType(type)
                            .status(stat)
                            .createdAt(Instant.now())
                            .build();
                    return loanApplicationRepository.save(toSave);
                });
    }

    private Mono<LoanType> findLoanTypeById(LoanApplication loanApplication) {
        Integer typeId = loanApplication.getLoanType() != null ? loanApplication.getLoanType().getId() : null;
        return Mono.defer(() ->
                loanTypeRepository.findById(typeId)
                        .switchIfEmpty(Mono.error(new LoanTypeNotFoundException(
                                "El tipo de préstamo seleccionado no existe", 400)))
        );
    }

    private Mono<LoanStatus> findPendingReviewLoanStatus() {
        return Mono.defer(() ->
                loanStatusRepository.findById(PENDING_REVIEW_ID)
                        .switchIfEmpty(Mono.error(new LoanStatusNotFoundException(
                                "Estado seleccionado no existe", 400)))
        );
    }

    private Mono<Void> verifyUserByDocumentNumber(LoanApplication loanApplication) {
        String document = String.valueOf(loanApplication.getNumberDocument());
        return Mono.defer(() ->
                authClient.getByDocument(document)
                        .flatMap(this::ensureUserExists)
                        .then()
        );
    }


    private Mono<AuthClient> ensureUserExists(AuthClient response) {
        return Boolean.TRUE.equals(response.getSuccess())
                ? Mono.just(response)
                : Mono.error(new UserNotFoundException("Usuario no encontrado", 404));
    }

    private Mono<Void> validAmount(LoanApplication loanApplication, LoanType loanType) {
        boolean belowMin = loanApplication.getAmount().compareTo(loanType.getMinimumAmount()) < 0;
        boolean aboveMax = loanApplication.getAmount().compareTo(loanType.getMaximumAmount()) > 0;

        if (belowMin || aboveMax) {
            return Mono.error(new AmountException(
                    "El monto digitado está fuera de los límites para el tipo de crédito seleccionado", 400));
        }
        return Mono.empty();
    }

}

