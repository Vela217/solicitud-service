package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.api.dto.GenericResponseDto;
import co.com.solicitudes.api.exception.DtoValidator;
import co.com.solicitudes.api.mapper.LoanRequestMapper;
import co.com.solicitudes.api.mapper.LoanResponseMapper;
import co.com.solicitudes.usecase.registerloanapplication.RegisterLoanApplicationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
                return req.bodyToMono(CreateLoanRequestDto.class)
                                .doOnNext(d -> log.info("📥 Solicitud recibida: {}", d))
                                .flatMap(validator::validate)
                                .map(mapper::toModel)
                                .flatMap(d -> useCase.registerLoanApplication(d)
                                                .as(tx::transactional))
                                .doOnSuccess(lr -> log.info("💾 Solicitud registrada {}", lr))
                                .map(responseMapper::toResponseDto)
                        .flatMap(responseDto -> {
                                GenericResponseDto<Object> genericResponse = GenericResponseDto.builder()
                                        .success(true)
                                        .message("Solicitud creada con éxito")
                                        .statusCode(HttpStatus.CREATED.value())
                                        .data(responseDto)
                                        .build();

                                return ServerResponse.status(HttpStatus.CREATED)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(genericResponse);
                        });
        }
}
