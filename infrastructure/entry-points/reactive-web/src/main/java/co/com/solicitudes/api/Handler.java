package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.api.dto.GenericResponseDto;
import co.com.solicitudes.api.exception.DtoValidator;
import co.com.solicitudes.api.mapper.LoanRequestMapper;
import co.com.solicitudes.api.mapper.LoanResponseMapper;
import co.com.solicitudes.usecase.registerloanapplication.RegisterLoanApplicationUseCase;
import exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class Handler {
        private final RegisterLoanApplicationUseCase useCase;
        private final TransactionalOperator tx;
        private final DtoValidator validator;
        private final LoanRequestMapper mapper;
        private final LoanResponseMapper responseMapper;

        public Mono<ServerResponse> createLoan(ServerRequest req) {
                Mono<JwtAuthenticationToken> auth = req.principal().cast(JwtAuthenticationToken.class);
            return Mono.zip(req.bodyToMono(CreateLoanRequestDto.class), auth)
                    .flatMap(tuple -> {
                        var dto = tuple.getT1();
                        var jwt = tuple.getT2().getToken();
                        log.info("Solicitud recibida: {} token: {} ", dto, jwt);
                        var docFromToken = jwt.getClaimAsString("numberDocument");
                        return validator.validate(dto) // 👉 primero validamos
                                .flatMap(validDto -> {
                                    if (validDto.numberDocument() != null
                                            && !validDto.numberDocument().equals(docFromToken)) {
                                        return Mono.error(new BusinessException(
                                                "No puedes crear solicitudes para otro usuario", 403));
                                    }

                                    var safeDto = new CreateLoanRequestDto(
                                            docFromToken, validDto.termMonths(), validDto.amount(), validDto.type()
                                    );

                                    return Mono.just(safeDto);
                                });
                    })
                    .map(mapper::toModel)
                    .flatMap(m -> useCase.registerLoanApplication(m).as(tx::transactional)
                            .doOnSuccess(lr -> log.info("💾 Solicitud registrada {}", lr))
                            .map(responseMapper::toResponseDto)
                            .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(GenericResponseDto.builder()
                                            .success(true)
                                            .message("Solicitud creada con éxito")
                                            .statusCode(HttpStatus.CREATED.value())
                                            .data(r)
                                            .build())));
        }
}
