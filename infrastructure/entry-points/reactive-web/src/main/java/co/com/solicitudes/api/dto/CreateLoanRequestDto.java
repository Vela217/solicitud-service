package co.com.solicitudes.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateLoanRequestDto(
        @NotBlank
        String numberDocument,
        @NotNull
        Integer termMonths,
        @NotNull
        BigDecimal amount,
        @NotNull
        Integer type
) {

}
