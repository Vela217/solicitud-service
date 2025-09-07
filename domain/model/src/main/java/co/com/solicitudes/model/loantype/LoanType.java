package co.com.solicitudes.model.loantype;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LoanType {
    private  Integer id;
    private  String  name;
    private  BigDecimal minimumAmount;
    private  BigDecimal maximumAmount;
    private Float interestRate;
    private  Boolean  automaticValidation;
}
