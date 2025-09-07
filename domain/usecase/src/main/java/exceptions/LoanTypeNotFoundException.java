package exceptions;

public class LoanTypeNotFoundException extends BusinessException{
    public LoanTypeNotFoundException(String message, int code) {
        super(message, code);
    }

}
