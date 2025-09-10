package co.com.solicitudes.r2dbc.entity;

import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("loan_application")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LoanApplicationEntity {
    @Id
    private UUID id;
    @Column("email")
    private String email;
    @Column("full_name")
    private String fullName;
    @Column("base_salary")
    private BigDecimal baseSalary;
    @Column("total_monthly_debt_approved_requests")
    private BigDecimal totalMonthlyDebtApprovedRequests;
    private String numberDocument;
    private BigDecimal amount;
    private Integer termMonths;
    private Integer loanTypeId;
    private Integer loanStatusId;   // PENDING_REVIEW, ...
    private Instant createdAt;
}
