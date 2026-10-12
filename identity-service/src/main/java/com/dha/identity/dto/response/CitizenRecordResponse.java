package com.dha.identity.dto.response;

import com.dha.identity.entity.CitizenRecord;

import java.time.LocalDate;

public record CitizenRecordResponse(
        String saIdNumber,
        String firstName,
        String middleName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,
        String nationality,
        String citizenshipStatus,
        String phoneNumber,
        String email,
        String residentialAddress,
        boolean hasPhoto) {

    public static CitizenRecordResponse from(CitizenRecord r, boolean hasPhoto) {
        return new CitizenRecordResponse(r.getSaIdNumber(), r.getFirstName(), r.getMiddleName(), r.getLastName(),
                r.getDateOfBirth(), r.getGender(), r.getNationality(), r.getCitizenshipStatus(),
                r.getPhoneNumber(), r.getEmail(), r.getResidentialAddress(), hasPhoto);
    }
}
