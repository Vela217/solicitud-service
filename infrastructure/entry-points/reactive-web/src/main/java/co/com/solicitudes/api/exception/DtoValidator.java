package co.com.solicitudes.api.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
@Component
@RequiredArgsConstructor
public class DtoValidator {

    private final Validator dtoValidator;

    public <T> Mono<T> validate(T dto) {
        var violations = dtoValidator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return Mono.just(dto);
    }
}