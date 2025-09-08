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
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.ObjectStreamException;
import java.math.BigDecimal;
import java.time.Instant;

@RequiredArgsConstructor
public class RegisterLoanApplicationUseCase {

    private final IAuthClient authClient;
    private final LoanTypeRepository loanTypeRepository;
    private final LoanStatusRepository loanStatusRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    private static final int PENDING_REVIEW_ID = 1;
    public Mono<LoanApplication> registerLoanApplication(LoanApplication draft) {
        return Mono.zip(
                verifyUserByDocumentNumber(draft),                                // T1: AuthClient (válido)
                findLoanTypeById(draft).flatMap(t -> validAmount(draft, t).thenReturn(t)), // T2: LoanType
                findPendingReviewLoanStatus()                                     // T3: LoanStatus
        ).flatMap(tuple3 -> {
            AuthClient client = tuple3.getT1();
            LoanType   type   = tuple3.getT2();
            LoanStatus stat   = tuple3.getT3();

            String email = null, name = null, lastName = null, numberDocument = null;
            BigDecimal baseSalary = null;

            Object data = client.getData();
            if (data instanceof java.util.Map<?,?> m) {
                numberDocument = m.get("numberDocument") != null ? String.valueOf(m.get("numberDocument")) : null;
                email         = (String) m.get("email");
                name          = (String) m.get("name");
                lastName      = (String) m.get("lastName");
                Object bs     = m.get("baseSalary");
                baseSalary    = bs != null ? new java.math.BigDecimal(String.valueOf(bs)) : null;
            }

            // Construye el agregado enriquecido (ajusta campos a tu entidad real)
            LoanApplication toSave = draft.toBuilder()
                    .loanType(type)
                    .status(stat)
                    .createdAt(Instant.now())
                    .numberDocument(numberDocument != null ? numberDocument : draft.getNumberDocument())
                    .email(email)
                    .totalMonthlyDebtApprovedRequests(new BigDecimal("1200000"))
                    .fullName(name+" "+lastName)
                    .baseSalary(baseSalary)
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

    private Mono<AuthClient> verifyUserByDocumentNumber(LoanApplication loanApplication) {
        String document = String.valueOf(loanApplication.getNumberDocument());
        return  authClient.getByDocument(document)
                        .flatMap(this::ensureUserExists);
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

