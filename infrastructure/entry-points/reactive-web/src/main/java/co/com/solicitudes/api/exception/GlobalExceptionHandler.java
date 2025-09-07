package co.com.solicitudes.api.exception;
import co.com.solicitudes.api.dto.GenericResponseDto;
import exceptions.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.rmi.RemoteException;

@Slf4j
@ControllerAdvice public class GlobalExceptionHandler  {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GenericResponseDto<Object>> handleValidation(ConstraintViolationException ex) {
        log.error("❌ Error de validación: {}", ex.getMessage());

        var errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .toList();

        var response = GenericResponseDto.builder()
                .success(false)
                .message("Validation failed")
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .data(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponseDto<Object>> handleGeneral(Exception ex) {
        log.error("🔥 Error inesperado: {}", ex.getMessage());

        var response = GenericResponseDto.builder()
                .success(false)
                .message("Unexpected error")
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .data(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GenericResponseDto<Object>> handleBusiness(BusinessException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        log.warn("⚠️ Regla de negocio violada: {} (code: {})", ex.getMessage(), ex.getCode());

        var response = GenericResponseDto.builder()
                .success(false)
                .message("Business rule violation")
                .statusCode(status.value())
                .data(ex.getMessage())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(RemoteException.class)
    public ResponseEntity<GenericResponseDto<Object>> handleWebClient(RemoteException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        log.error("🌐 Error HTTP remoto: {} - {}", status, ex.getMessage());

        var response = GenericResponseDto.builder()
                .success(false)
                .message("Remote HTTP error")
                .statusCode(status.value())
                .data(ex.getMessage())
                .build();

        return ResponseEntity.status(status).body(response);
    }

}