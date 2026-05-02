package com.javainternshipapigateway.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CompositeRegistrationRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Size(min = 2, max = 255) String surname,
        @NotNull LocalDate birthDate,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 255) String login,
        @NotBlank String password
) {

    public CreateUserBody userProfile() {
        return new CreateUserBody(name, surname, birthDate, email);
    }

    public record CreateUserBody(String name, String surname, LocalDate birthDate, String email) {
    }

    public record RegisterAuthBody(Long userId, String login, String password, String email) {
    }
}
