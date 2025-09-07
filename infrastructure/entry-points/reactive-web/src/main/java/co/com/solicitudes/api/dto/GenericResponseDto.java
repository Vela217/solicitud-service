package co.com.solicitudes.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponseDto<T> {
    private int statusCode;
    private Boolean success;
    private String message;
    private Object data;
}
