package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import co.com.solicitudes.api.dto.GenericResponseDto;
import co.com.solicitudes.api.exception.DtoValidator;
import co.com.solicitudes.api.mapper.LoanRequestMapper;
import co.com.solicitudes.api.mapper.LoanResponseMapper;
import co.com.solicitudes.usecase.listforreview.ListForReviewUseCase;
import co.com.solicitudes.usecase.registerloanapplication.RegisterLoanApplicationUseCase;
import exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
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
    private final ListForReviewUseCase listForReviewUseCase;

    public Mono<ServerResponse> createLoan(ServerRequest req) {

        Mono<JwtAuthenticationToken> auth =
                ReactiveSecurityContextHolder.getContext()
                        .map(ctx -> (JwtAuthenticationToken) ctx.getAuthentication());

        return Mono.zip(req.bodyToMono(CreateLoanRequestDto.class), auth)
                .flatMap(tuple -> {
                    var dto = tuple.getT1();
                    var jwt = tuple.getT2().getToken();
                    log.info("Solicitud recibida: {} token: {}", dto, jwt.getTokenValue());

                    var docFromToken = jwt.getClaimAsString("numberDocument");

                    return validator.validate(dto)
                            .flatMap(validDto -> {
                                if (validDto.numberDocument() != null
                                        && !validDto.numberDocument().equals(docFromToken)) {
                                    log.info("No puedes crear solicitudes para otro usuario");
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
                                        .build())))
                .onErrorResume(BusinessException.class, ex ->
                        ServerResponse.status(HttpStatus.FORBIDDEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(GenericResponseDto.builder()
                                        .success(false)
                                        .message(ex.getMessage())
                                        .statusCode(HttpStatus.FORBIDDEN.value())
                                        .data(null)
                                        .build()))
                .switchIfEmpty(ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(GenericResponseDto.builder()
                                .success(false)
                                .message("Token inválido o no provisto")
                                .statusCode(HttpStatus.UNAUTHORIZED.value())
                                .data(null)
                                .build()));
    }

    public Mono<ServerResponse> list(ServerRequest req) {
        int page = Integer.parseInt(req.queryParam("page").orElse("0"));
        int size = Integer.parseInt(req.queryParam("size").orElse("10"));

        log.info("[review-list] page={} size={}", page, size);

        return listForReviewUseCase.list(page, size)
                .map(p -> GenericResponseDto.builder()
                        .success(true)
                        .message("Listado generado")
                        .statusCode(200)
                        .data(p) // PageImpl serializa bien: content, totalElements, totalPages, etc.
                        .build())
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(dto));
    }

}