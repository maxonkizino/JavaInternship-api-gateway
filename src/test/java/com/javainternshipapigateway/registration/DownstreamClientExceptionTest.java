package com.javainternshipapigateway.registration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamClientExceptionTest {

    @Test
    void exposesStatusAndBody() {
        DownstreamClientException ex = new DownstreamClientException(HttpStatus.BAD_REQUEST, "duplicate");

        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(ex.getBody()).isEqualTo("duplicate");
    }
}
