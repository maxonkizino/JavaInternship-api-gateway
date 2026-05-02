package com.javainternshipapigateway.registration;

import org.springframework.http.HttpStatusCode;

public class DownstreamClientException extends RuntimeException {

    private final HttpStatusCode status;
    private final String body;

    public DownstreamClientException(HttpStatusCode status, String body) {
        super("Downstream error " + status + ": " + body);
        this.status = status;
        this.body = body;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
