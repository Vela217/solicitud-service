package constant;

public enum LoanStatusCode {
    APROBADA(4),
    RECHAZADA(2);

    private final int code;

    LoanStatusCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
