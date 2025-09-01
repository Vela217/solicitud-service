package co.com.solicitudes.model.loanstatus;
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
public class LoanStatus {
    private  Integer id;                 // loan_status_id
    private  String  name;               // "Pendiente de revisión", etc.
    private  String  description;
}
