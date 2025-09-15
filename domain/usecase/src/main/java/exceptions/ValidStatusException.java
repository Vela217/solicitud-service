package exceptions;

public class ValidStatusException extends BusinessException {
    public ValidStatusException(String message, int code) {
        super(message, code);
    }
}
