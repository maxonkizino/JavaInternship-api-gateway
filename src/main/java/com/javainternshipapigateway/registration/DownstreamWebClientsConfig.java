package com.javainternshipapigateway.registration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class DownstreamWebClientsConfig {

    @Bean
    @Qualifier("userServiceWebClient")
    public WebClient userServiceWebClient(
            WebClient.Builder builder,
            @Value("${app.downstream.user-service-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    @Qualifier("authServiceWebClient")
    public WebClient authServiceWebClient(
            WebClient.Builder builder,
            @Value("${app.downstream.auth-service-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
