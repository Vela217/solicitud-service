package co.com.solicitudes.api.mapper;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loantype.LoanType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanRequestMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true) // se setea en capa de negocio
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(source = "type", target = "loanType")
    LoanApplication toModel(CreateLoanRequestDto dto);

    default LoanType map(Integer typeId) {
        return (typeId == null) ? null : LoanType.builder().id(typeId).build();
    }
}
