package com.javainternshipapigateway.config;

import com.javainternshipapigateway.support.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "USER_SERVICE_URI=http://127.0.0.1:1",
                "AUTH_SERVICE_URI=http://127.0.0.1:1",
                "ORDER_SERVICE_URI=http://127.0.0.1:1",
                "PAYMENT_SERVICE_URI=http://127.0.0.1:1",
                "spring.security.oauth2.resourceserver.jwt.secret-key=test-secret-key-at-least-256-bits-long-for-hs256-xxxxxxxx",
                "app.gateway-internal-secret=test-internal",
                "app.downstream.user-service-url=http://127.0.0.1:1",
                "app.downstream.auth-service-url=http://127.0.0.1:1"
        })
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTest {

    private static final String JWT_SECRET = "test-secret-key-at-least-256-bits-long-for-hs256-xxxxxxxx";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealthPermittedWithoutToken() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRouteRejectedWithoutToken() {
        webTestClient.get()
                .uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void postRegistrationPermittedWithoutToken() {
        String body = """
                {"name":"Jo","surname":"Do","birthDate":"1990-01-01","email":"a@b.com","login":"uu","password":"secret12"}""";
        webTestClient.post()
                .uri("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .value(s -> assertThat(s).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void protectedRouteWithValidJwtPassesAuthenticationLayer() {
        String token = JwtTestSupport.hs256Token(JWT_SECRET, "user-1");
        webTestClient.get()
                .uri("/api/users/1")
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .value(s -> assertThat(s).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }
}
