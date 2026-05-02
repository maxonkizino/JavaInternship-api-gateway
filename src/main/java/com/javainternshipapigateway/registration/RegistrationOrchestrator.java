package com.javainternshipapigateway.registration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class RegistrationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RegistrationOrchestrator.class);

    private final Validator validator;
    private final WebClient userServiceWebClient;
    private final WebClient authServiceWebClient;
    private final String gatewayInternalSecret;

    public RegistrationOrchestrator(
            Validator validator,
            @Qualifier("userServiceWebClient") WebClient userServiceWebClient,
            @Qualifier("authServiceWebClient") WebClient authServiceWebClient,
            @Value("${app.gateway-internal-secret}") String gatewayInternalSecret) {
        this.validator = validator;
        this.userServiceWebClient = userServiceWebClient;
        this.authServiceWebClient = authServiceWebClient;
        this.gatewayInternalSecret = gatewayInternalSecret;
    }

    public Mono<ServerResponse> handle(ServerRequest request) {
        return request.bodyToMono(CompositeRegistrationRequest.class)
                .doOnNext(body -> log.info("Registration request received: login={}, email={}", body.login(), body.email()))
                .flatMap(this::validate)
                .flatMap(this::registerUserThenCredentials)
                .flatMap(userId -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("userId", userId)))
                .onErrorResume(ResponseStatusException.class, ex -> {
                    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
                    if (status == null) {
                        status = HttpStatus.BAD_REQUEST;
                    }
                    String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
                    return ServerResponse.status(status).bodyValue(Map.of("error", reason));
                })
                .onErrorResume(DownstreamClientException.class, ex -> ServerResponse
                        .status(ex.getStatus())
                        .bodyValue(Map.of("error", ex.getBody())))
                .onErrorResume(WebClientResponseException.class, ex -> ServerResponse
                        .status(ex.getStatusCode())
                        .bodyValue(Map.of("error", ex.getResponseBodyAsString())));
    }

    private Mono<CompositeRegistrationRequest> validate(CompositeRegistrationRequest body) {
        Set<ConstraintViolation<CompositeRegistrationRequest>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, msg));
        }
        return Mono.just(body);
    }

    private Mono<Long> registerUserThenCredentials(CompositeRegistrationRequest body) {
        log.info("Starting registration orchestration for login={}", body.login());
        return userServiceWebClient.post()
                .uri("/api/users/internal/register")
                .header("X-Gateway-Internal", gatewayInternalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.userProfile())
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new DownstreamClientException(response.statusCode(),
                                b.isBlank() ? "user-service returned error without body" : b))
                        .flatMap(Mono::error))
                .bodyToMono(UserProfileResponse.class)
                .switchIfEmpty(Mono.error(new DownstreamClientException(
                        HttpStatus.BAD_GATEWAY, "user-service returned empty body")))
                .flatMap(profile -> {
                    Long userId = profile.id();
                    log.info("User profile created in user-service: userId={}", userId);
                    var authBody = new CompositeRegistrationRequest.RegisterAuthBody(
                            userId, body.login(), body.password(), body.email());
                    return authServiceWebClient.post()
                            .uri("/auth/credentials")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(authBody)
                            .retrieve()
                            .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errBody -> rollback(userId)
                                            .then(Mono.error(new DownstreamClientException(response.statusCode(), errBody)))))
                            .bodyToMono(Long.class)
                            .switchIfEmpty(Mono.error(new DownstreamClientException(
                                    HttpStatus.BAD_GATEWAY, "auth-service returned empty body")))
                            .doOnNext(authId -> log.info("Credentials created in auth-service: authId={}, userId={}", authId, userId))
                            .thenReturn(userId);
                })
                .onErrorMap(TimeoutException.class,
                        ex -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Registration timed out", ex))
                .doOnError(ex -> log.error("Registration orchestration failed for login={}", body.login(), ex));
    }

    private Mono<Void> rollback(Long userId) {
        return userServiceWebClient.delete()
                .uri("/api/users/internal/register/{userId}/rollback", userId)
                .header("X-Gateway-Internal", gatewayInternalSecret)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("Rollback failed for userId={}", userId, e));
    }
}
