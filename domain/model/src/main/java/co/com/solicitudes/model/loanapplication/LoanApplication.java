package co.com.solicitudes.model.loanapplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LoanApplication {
    private  UUID id;
    private  String numberDocument;
    private BigDecimal amount;
    private Integer termMonths;
    private LoanType loanType;
    private LoanStatus status;
    private Instant createdAt;
}
