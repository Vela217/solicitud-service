package co.com.solicitudes.model.authclient;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AuthClient {

    private Integer statusCode;
    private Boolean success;
    private String message;
    private Object data;
}
