package co.com.solicitudes.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("loan_type")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LoanTypeEntity {
    @Id
    private Integer id;
    private  String  name;
    private  BigDecimal minimumAmount;
    private  BigDecimal maximumAmount;
    private Float interestRate;
    private  Boolean  automaticValidation;
}
