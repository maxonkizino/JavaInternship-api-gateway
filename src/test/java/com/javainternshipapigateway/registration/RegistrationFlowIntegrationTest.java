package com.javainternshipapigateway.registration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("resource")
class RegistrationFlowIntegrationTest {

    static final MockWebServer userServer = new MockWebServer();
    static final MockWebServer authServer = new MockWebServer();

    static {
        try {
            userServer.start();
            authServer.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("app.downstream.user-service-url", () -> "http://127.0.0.1:" + userServer.getPort());
        registry.add("app.downstream.auth-service-url", () -> "http://127.0.0.1:" + authServer.getPort());
        registry.add("spring.security.oauth2.resourceserver.jwt.secret-key",
                () -> "test-secret-key-at-least-256-bits-long-for-hs256-xxxxxxxx");
        registry.add("app.gateway-internal-secret", () -> "test-gateway-secret");
        registry.add("USER_SERVICE_URI", () -> "http://127.0.0.1:1");
        registry.add("AUTH_SERVICE_URI", () -> "http://127.0.0.1:1");
        registry.add("ORDER_SERVICE_URI", () -> "http://127.0.0.1:1");
        registry.add("PAYMENT_SERVICE_URI", () -> "http://127.0.0.1:1");
    }

    @AfterAll
    static void tearDown() throws IOException {
        userServer.shutdown();
        authServer.shutdown();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @Order(1)
    void registrationReturnsCreatedWhenUserAndAuthSucceed() throws InterruptedException {
        userServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":42,\"name\":\"John\",\"surname\":\"Doe\",\"birthDate\":\"1990-05-20\",\"email\":\"j@d.com\",\"active\":true}"));
        authServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("99"));

        String json = """
                {"name":"John","surname":"Doe","birthDate":"1990-05-20","email":"j@d.com","login":"jdoe","password":"secret12"}""";

        webTestClient.post()
                .uri("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(42);

        assertThat(userServer.takeRequest(2, TimeUnit.SECONDS).getPath()).isEqualTo("/api/users/internal/register");
        assertThat(authServer.takeRequest(2, TimeUnit.SECONDS).getPath()).isEqualTo("/auth/credentials");
    }

    @Test
    @Order(2)
    void registrationReturnsBadRequestWhenValidationFails() {
        String json = """
                {"name":"","surname":"Doe","birthDate":"1990-05-20","email":"bad","login":"u","password":"x"}""";

        webTestClient.post()
                .uri("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    @Order(3)
    void registrationCallsRollbackWhenAuthFailsAfterUserCreated() throws InterruptedException {
        userServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"id\":7,\"name\":\"A\",\"surname\":\"B\",\"birthDate\":\"2000-01-01\",\"email\":\"a@b.com\",\"active\":true}"));
        authServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"duplicate\"}"));
        userServer.enqueue(new MockResponse().setResponseCode(204));

        String json = """
                {"name":"Al","surname":"Bo","birthDate":"2000-01-01","email":"a@b.com","login":"ab","password":"secret12"}""";

        webTestClient.post()
                .uri("/api/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isBadRequest();

        RecordedRequest createUser = userServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(createUser.getMethod()).isEqualTo("POST");
        assertThat(createUser.getPath()).isEqualTo("/api/users/internal/register");
        assertThat(createUser.getHeader("X-Gateway-Internal")).isEqualTo("test-gateway-secret");

        RecordedRequest rollback = userServer.takeRequest(2, TimeUnit.SECONDS);
        assertThat(rollback.getMethod()).isEqualTo("DELETE");
        assertThat(rollback.getPath()).isEqualTo("/api/users/internal/register/7/rollback");
        assertThat(rollback.getHeader("X-Gateway-Internal")).isEqualTo("test-gateway-secret");
    }
}
