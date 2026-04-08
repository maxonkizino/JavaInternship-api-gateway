package com.javainternshipapigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class JavaInternshipApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaInternshipApiGatewayApplication.class, args);
    }

}
