package com.javainternshipapigateway.registration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeRegistrationRequestValidationTest {

    @Test
    void validRequestHasNoViolations() {
        CompositeRegistrationRequest request = new CompositeRegistrationRequest(
                "John", "Doe", LocalDate.of(1990, 1, 1), "j@d.com", "jdoe", "password");
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Test
    void blankNameProducesViolation() {
        CompositeRegistrationRequest request = new CompositeRegistrationRequest(
                "", "Doe", LocalDate.of(1990, 1, 1), "j@d.com", "jdoe", "password");
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(request)).isNotEmpty();
        }
    }
}
