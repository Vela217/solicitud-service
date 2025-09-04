package co.com.solicitudes.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResponseCreateLoan(
                UUID id,
                String numberDocument,
                Integer termMonths,
                BigDecimal amount,
                LoanTypeInfo loanType,
                LoanStatusInfo status,
                Instant createdAt)
{
        public record LoanTypeInfo(
                        Integer id,
                        String name,
                        BigDecimal minimumAmount,
                        BigDecimal maximumAmount,
                        Float interestRate,
                        Boolean automaticValidation) {
        }

        public record LoanStatusInfo(
                        Integer id,
                        String name,
                        String description) {
        }
}
