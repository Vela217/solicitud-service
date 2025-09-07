package exceptions;

public class UserNotFoundException extends BusinessException {
    public UserNotFoundException(String message, int code) {
        super(message, code);
    }

}


