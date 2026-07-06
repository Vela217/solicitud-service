package co.com.solicitudes.sqs.sender;

import co.com.solicitudes.model.loanapplication.LoanApplication;
import co.com.solicitudes.model.loandecisionpublisher.gateways.LoanDecisionPublisher;
import co.com.solicitudes.sqs.sender.config.SQSSenderProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Service
@Log4j2
@RequiredArgsConstructor
public class SQSSender  implements LoanDecisionPublisher {
    private final SQSSenderProperties properties;
    private final SqsAsyncClient client;
    private final ObjectMapper mapper;

    public Mono<String> send(String message) {
        return Mono.fromCallable(() -> buildRequest(message))
                .flatMap(request -> Mono.fromFuture(client.sendMessage(request)))
                .doOnNext(response -> log.debug("Message sent {}", response.messageId()))
                .map(SendMessageResponse::messageId);
    }

    private SendMessageRequest buildRequest(String message) {
        return SendMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .messageBody(message)
                .build();
    }

    @Override
    public Mono<String> publish(LoanApplication app) {
        log.info("event {}", app);
        final String dec =  app.getStatus().getName().toUpperCase();
        final String subject = ("APROBADA".equals(dec) ? "Solicitud APROBADA" :"Solicitud RECHAZADA");

        final String body = ("APROBADA".equals(dec))
                ? String.format("¡Felicitaciones! Tu solicitud ID: %s fue APROBADA.", app.getId())
                : String.format("Lo sentimos. Tu solicitud ID: %s fue RECHAZADA.", app.getId());

        return Mono.fromCallable(() -> {
            var payload = new java.util.HashMap<String, Object>();
            payload.put("to", app.getEmail());
            payload.put("subject", subject);
            payload.put("body", body);
            return mapper.writeValueAsString(payload);
        }).flatMap(this::send);
    }

}
