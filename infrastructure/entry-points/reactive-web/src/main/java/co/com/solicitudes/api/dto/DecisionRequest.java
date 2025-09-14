package co.com.solicitudes.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DecisionRequest(
        @NotBlank
        String decision,

        @NotNull
        UUID id
) {
}
