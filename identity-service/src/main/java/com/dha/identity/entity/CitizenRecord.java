package com.dha.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Home Affairs' own record of a citizen — the National Population Register,
 * simplified for this prototype. Seeded by seed.CitizenRecordSeeder, and
 * (DEV ONLY) editable by an admin through the admin dashboard's "Citizen
 * Records" tool, which stands in for DHA's own systems. Citizens can never
 * write to it. The matching ID photo lives in CitizenPhoto and is what a live
 * face scan is checked against. A citizen logging in must state their SA ID number and name
 * matching a row here — proving they are who Home Affairs says that ID
 * belongs to — not the other way around.
 */
@Entity
@Table(name = "citizen_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenRecord {

    @Id
    @Column(name = "sa_id_number", length = 13)
    private String saIdNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String gender;

    private String nationality;

    @Column(name = "citizenship_status")
    private String citizenshipStatus;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String email;

    @Column(name = "residential_address", length = 500)
    private String residentialAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
