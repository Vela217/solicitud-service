package co.com.solicitudes.api.mapper;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loantype.LoanType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanRequestMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)

    // campos que sí vienen del DTO
    @Mapping(target = "numberDocument", source = "numberDocument")
    @Mapping(target = "termMonths", source = "termMonths")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "loanType", source = "type") // usa el default map() de abajo// usa el default map() de abajo
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    @Mapping(target = "baseSalary", ignore = true)
    @Mapping(target = "totalMonthlyDebtApprovedRequests", ignore = true)
    LoanApplication toModel(CreateLoanRequestDto dto);

    default LoanType map(Integer typeId) {
        return (typeId == null) ? null : LoanType.builder().id(typeId).build();
    }
}
