package co.com.solicitudes.api.dto;

import java.util.List;

public record PagedResponseDto<T>(
        boolean success,
        String message,
        int statusCode,
        List<T> data,
        int page,
        int size,
        long total
) {
    public static <T> PagedResponseDto<T> ok(List<T> data, int page, int size, long total) {
        return new PagedResponseDto<>(true, "OK", 200, data, page, size, total);
    }
}
