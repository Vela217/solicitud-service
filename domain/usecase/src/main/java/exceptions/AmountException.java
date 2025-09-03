package exceptions;

public class AmountException extends BusinessException {
    public AmountException(String message, int code) {
        super(message, code);
    }

}


