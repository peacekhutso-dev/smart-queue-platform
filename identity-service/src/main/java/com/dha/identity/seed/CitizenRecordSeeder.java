package com.dha.identity.seed;

import com.dha.identity.entity.CitizenRecord;
import com.dha.identity.repository.CitizenRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Home Affairs' "already has this on record" data — the National Population
 * Register, simulated. There's no real government population register to
 * draw from in this prototype, so this seeder is it: every row here is a
 * citizen a login's "confirm your identity" step (AuthService.confirmIdentity)
 * can match against. Ten dev/test identities below — register in the
 * frontend with the exact first/last name + SA ID number for whichever one
 * you want to test as (see document-service's seeded reference photo, which
 * is linked to whichever of these accounts actually get registered — one
 * shared photo works for all of them since there's only one real tester).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CitizenRecordSeeder implements CommandLineRunner {

    private final CitizenRecordRepository citizenRecordRepository;

    @Override
    public void run(String... args) {
        seed("9001015008088", "Naledi", "Pandor", LocalDate.of(1990, 1, 1), "Female",
                "+27820000001", "naledi.pandor@example.co.za", "12 Church Street, Pretoria, Gauteng");
        seed("8506155123084", "Thabo", "Mbeki", LocalDate.of(1985, 6, 15), "Male",
                "+27820000002", "thabo.mbeki@example.co.za", "45 Long Street, Cape Town, Western Cape");
        seed("9203101234087", "Zanele", "Mokoena", LocalDate.of(1992, 3, 10), "Female",
                "+27820000003", "zanele.mokoena@example.co.za", "7 Market Street, Durban, KwaZulu-Natal");
        seed("8811225678087", "Sipho", "Ndlovu", LocalDate.of(1988, 11, 22), "Male",
                "+27820000004", "sipho.ndlovu@example.co.za", "23 Voortrekker Road, Bloemfontein, Free State");
        seed("9507042345087", "Lerato", "Dlamini", LocalDate.of(1995, 7, 4), "Female",
                "+27820000005", "lerato.dlamini@example.co.za", "89 Beach Road, Port Elizabeth, Eastern Cape");
        seed("8712306789084", "Bongani", "Zulu", LocalDate.of(1987, 12, 30), "Male",
                "+27820000006", "bongani.zulu@example.co.za", "15 Kerk Street, Polokwane, Limpopo");
        seed("9305183456080", "Aisha", "Patel", LocalDate.of(1993, 5, 18), "Female",
                "+27820000007", "aisha.patel@example.co.za", "31 Nelson Mandela Drive, Nelspruit, Mpumalanga");
        seed("8909267890089", "Johan", "Botha", LocalDate.of(1989, 9, 26), "Male",
                "+27820000008", "johan.botha@example.co.za", "56 Main Reef Road, Kimberley, Northern Cape");
        seed("9612074567085", "Nomvula", "Khumalo", LocalDate.of(1996, 12, 7), "Female",
                "+27820000009", "nomvula.khumalo@example.co.za", "4 President Street, Mahikeng, North West");
        seed("8408138901084", "Pieter", "van der Merwe", LocalDate.of(1984, 8, 13), "Male",
                "+27820000010", "pieter.vandermerwe@example.co.za", "2 Dorp Street, Stellenbosch, Western Cape");
    }

    private void seed(String saIdNumber, String firstName, String lastName, LocalDate dob, String gender,
                       String phoneNumber, String email, String address) {
        if (citizenRecordRepository.existsById(saIdNumber)) return;
        citizenRecordRepository.save(CitizenRecord.builder()
                .saIdNumber(saIdNumber)
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dob)
                .gender(gender)
                .nationality("South African")
                .citizenshipStatus("Citizen by birth")
                .phoneNumber(phoneNumber)
                .email(email)
                .residentialAddress(address)
                .build());
        log.info("Seeded Home Affairs citizen record for SA ID {}", saIdNumber);
    }
}
