package co.com.solicitudes.api;

import co.com.solicitudes.api.dto.CreateLoanRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@Tag(name = "Solicitudes", description = "Gestión de solicitudes de crédito")

public class RouterRest {
    @Bean
    @RouterOperations({
            // POST /api/v1/solicitud
            @RouterOperation(
                    path = "/api/v1/solicitud",
                    method = RequestMethod.POST,
                    beanClass = Handler.class,
                    beanMethod = "createLoan",
                    operation = @Operation(
                            operationId = "crearSolicitud",
                            summary = "Crear una solicitud de crédito",
                            tags = {"Solicitudes"},
                            requestBody = @RequestBody(
                                    required = true,
                                    description = "Datos de la solicitud",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = CreateLoanRequestDto.class)
                                    )
                            ),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "201",
                                            description = "Solicitud creada con éxito",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                            {
                              "statusCode": 201,
                              "success": true,
                              "message": "Solicitud creada con éxito",
                              "data": {
                                "id": "0ab364cd-d618-4b5b-aa32-a5485e6c6420",
                                "numberDocument": "12345678",
                                "termMonths": 12,
                                "amount": 1200000,
                                "loanType": {
                                  "id": 1,
                                  "name": "Préstamo Personal",
                                  "minimumAmount": 500000.00,
                                  "maximumAmount": 20000000.00,
                                  "interestRate": 12.5,
                                  "automaticValidation": true
                                },
                                "status": {
                                  "id": 1,
                                  "name": "Pendiente de revisión",
                                  "description": "La solicitud fue registrada y está en proceso de revisión"
                                },
                                "createdAt": "2025-09-03T07:50:45.188606300Z"
                              }
                            }
                            """)
                                            )
                                    ),
                                    // 400 - validación
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Validación de datos",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                            {
                              "statusCode": 400,
                              "success": false,
                              "message": "Validation failed",
                              "data": [
                                "numberDocument no debe estar vacío",
                                "termMonths no debe ser nulo"
                              ]
                            }
                            """)
                                            )
                                    ),
                                    // 400 - regla de negocio
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Regla de negocio",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                            {
                              "statusCode": 400,
                              "success": false,
                              "message": "Business rule violation",
                              "data": "El monto digitado está fuera de los límites para el tipo de crédito seleccionado"
                            }
                            """)
                                            )
                                    ),
                                    // 404 - remoto/usuario no encontrado
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "Recurso no encontrado",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                            {
                              "statusCode": 404,
                              "success": false,
                              "message": "Remote HTTP error",
                              "data": "Usuario no encontrado"
                            }
                            """)
                                            )
                                    )
                            }
                    )

            ),
            // GET /api/v1/solicitud  (listar)
            @RouterOperation(
                    path = "/api/v1/solicitud",
                    method = RequestMethod.GET,
                    beanClass = Handler.class,
                    beanMethod = "list",
                    operation = @Operation(
                            operationId = "listarSolicitudes",
                            summary = "Listar solicitudes de crédito (paginado)",
                            tags = {"Solicitudes"},
                            parameters = {
                                    @io.swagger.v3.oas.annotations.Parameter(
                                            name = "page",
                                            description = "Número de página (0-based)",
                                            required = false,
                                            example = "0",
                                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")
                                    ),
                                    @io.swagger.v3.oas.annotations.Parameter(
                                            name = "size",
                                            description = "Tamaño de página",
                                            required = false,
                                            example = "10",
                                            in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", minimum = "1", defaultValue = "10")
                                    )
                            },
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Listado generado",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example =
                                                            """
                                                            {
                                                              "success": true,
                                                              "message": "Listado generado",
                                                              "statusCode": 200,
                                                              "data": {
                                                                "content": [
                                                                  {
                                                                    "id": "a5cd6489-0aca-44e7-9701-0cfe2f87fa53",
                                                                    "numberDocument": "11111",
                                                                    "email": "cliente@email.com",
                                                                    "fullName": "Cliente Tovar",
                                                                    "amount": 2000000.00,
                                                                    "baseSalary": 2000000,
                                                                    "termMonths": 12,
                                                                    "loanType": { "id": 1, "name": "Personal" },
                                                                    "status": { "id": 1, "name": "Pendiente de revisión" },
                                                                    "createdAt": "2025-09-08T19:53:31.789228Z"
                                                                  }
                                                                ],
                                                                "total": 19,
                                                                "page": 0,
                                                                "size": 10,
                                                                "totalPages": 2
                                                              }
                                                            }
                                                            """
                                                    )
                                            )
                                    )
                            }
                    )

            ),
            // PUT /api/v1/solicitud  (decidir: aprobar/rechazar)
            @RouterOperation(
                    path = "/api/v1/solicitud",
                    method = RequestMethod.PUT,
                    beanClass = Handler.class,
                    beanMethod = "decide",
                    operation = @Operation(
                            operationId = "decidirSolicitud",
                            summary = "Decidir (aprobar/rechazar) una solicitud de crédito",
                            description = "Actualiza el estado de una solicitud existente en función de la decisión enviada.",
                            tags = {"Solicitudes"},
                            requestBody = @RequestBody(
                                    required = true,
                                    description = "Decisión sobre la solicitud",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = co.com.solicitudes.api.dto.DecisionRequest.class),
                                            examples = {
                                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                            name = "Aprobar solicitud",
                                                            value = """
                                                        {
                                                          "decision": "APPROVE",
                                                          "id": "0ab364cd-d618-4b5b-aa32-a5485e6c6420"
                                                        }
                                                        """
                                                    ),
                                                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                                                            name = "Rechazar solicitud",
                                                            value = """
                                                        {
                                                          "decision": "REJECT",
                                                          "id": "0ab364cd-d618-4b5b-aa32-a5485e6c6420"
                                                        }
                                                        """
                                                    )
                                            }
                                    )
                            ),
                            responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Solicitud actualizada",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                                                {
                                                  "success": true,
                                                  "message": "Solicitud actualizada",
                                                  "statusCode": 200,
                                                  "data": {
                                                    "id": "0ab364cd-d618-4b5b-aa32-a5485e6c6420",
                                                    "numberDocument": "12345678",
                                                    "amount": 1200000,
                                                    "termMonths": 12,
                                                    "status": {
                                                      "id": 2,
                                                      "name": "Aprobada",
                                                      "description": "La solicitud fue aprobada"
                                                    },
                                                    "decidedAt": "2025-09-10T14:22:31.123Z"
                                                  }
                                                }
                                                """)
                                            )
                                    ),
                                    // 400 - validación (payload inválido)
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "Validación de datos",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                                                {
                                                  "statusCode": 400,
                                                  "success": false,
                                                  "message": "Validation failed",
                                                  "data": [
                                                    "decision no debe estar vacío",
                                                    "id no debe ser nulo"
                                                  ]
                                                }
                                                """)
                                            )
                                    ),
                                    // 404 - solicitud no existe
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "Recurso no encontrado",
                                            content = @Content(
                                                    mediaType = "application/json",
                                                    schema = @Schema(example = """
                                                {
                                                  "statusCode": 404,
                                                  "success": false,
                                                  "message": "Solicitud no encontrada",
                                                  "data": "No existe una solicitud con el id proporcionado"
                                                }
                                                """)
                                            )
                                    )
                            }
                    )
            )
    })

    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return route(POST("/api/v1/solicitud"), handler::createLoan)
                .andRoute(GET("/api/v1/solicitud"), handler::list)
                .andRoute(PUT("/api/v1/solicitud"), handler::decide);
    }
}
