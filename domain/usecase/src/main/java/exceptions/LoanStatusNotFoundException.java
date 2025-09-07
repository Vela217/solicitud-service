package exceptions;

public class LoanStatusNotFoundException extends BusinessException {
    public LoanStatusNotFoundException(String message, int code) {
        super(message, code);
    }

}

