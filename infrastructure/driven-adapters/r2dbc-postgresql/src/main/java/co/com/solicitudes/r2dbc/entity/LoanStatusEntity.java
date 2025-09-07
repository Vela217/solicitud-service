package co.com.solicitudes.r2dbc.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;


@Table("loan_status")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LoanStatusEntity {
    @Id
    private Integer id;
    private String name;
    private String  description;
}