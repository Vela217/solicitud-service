package co.com.solicitudes.api.mapper;

import co.com.solicitudes.api.dto.ResponseCreateLoan;
import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loanstatus.LoanStatus;
import co.com.solicitudes.model.loantype.LoanType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface LoanResponseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "numberDocument", source = "numberDocument")
    @Mapping(target = "termMonths", source = "termMonths")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "loanType", source = "loanType")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "createdAt", source = "createdAt")

    ResponseCreateLoan toResponseDto(LoanApplication loanApplication);

    // Mapeo del tipo de préstamo
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "minimumAmount", source = "minimumAmount")
    @Mapping(target = "maximumAmount", source = "maximumAmount")
    @Mapping(target = "interestRate", source = "interestRate")
    @Mapping(target = "automaticValidation", source = "automaticValidation")
    ResponseCreateLoan.LoanTypeInfo mapLoanType(LoanType loanType);

    // Mapeo del estado del préstamo
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    ResponseCreateLoan.LoanStatusInfo mapLoanStatus(LoanStatus loanStatus);

}
