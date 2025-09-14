package co.com.solicitudes.model.loanapplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString
public class LoanApplication {
    private  UUID id;
    private  String numberDocument;
    private String email;
    private String fullName;
    private BigDecimal amount;
    private BigDecimal baseSalary;
    private BigDecimal totalMonthlyDebtApprovedRequests;
    private Integer termMonths;
    private LoanType loanType;
    private LoanStatus status;
    private Instant createdAt;
}
