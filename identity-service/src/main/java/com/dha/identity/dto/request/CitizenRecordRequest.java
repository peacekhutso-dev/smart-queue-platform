package com.dha.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CitizenRecordRequest {

    @NotBlank
    @Pattern(regexp = "\\d{13}", message = "SA ID number must be 13 digits")
    private String saIdNumber;

    @NotBlank
    private String firstName;

    private String middleName;

    @NotBlank
    private String lastName;

    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private String citizenshipStatus;
    private String phoneNumber;
    private String email;
    private String residentialAddress;
}
