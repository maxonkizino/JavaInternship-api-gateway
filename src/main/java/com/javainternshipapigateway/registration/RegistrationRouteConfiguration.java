package com.javainternshipapigateway.registration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@Configuration
public class RegistrationRouteConfiguration {

    @Bean
    public RouterFunction<ServerResponse> registrationRoute(RegistrationOrchestrator orchestrator) {
        return RouterFunctions.route(POST("/api/registration"), orchestrator::handle);
    }
}
