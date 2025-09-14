package exceptions;

public class LoanApplicationNotFoundException extends BusinessException {
    public LoanApplicationNotFoundException(String message, int code) {
        super(message, code);
    }
}
