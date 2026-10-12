package com.dha.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * The photo Home Affairs holds for a citizen (their ID photo), keyed by SA ID
 * number like CitizenRecord. A live face scan at registration/login is
 * compared against this. Kept in its own table so listing citizen records
 * never loads image bytes.
 *
 * DEV ONLY: in this prototype the photos are loaded by an admin through the
 * admin dashboard's "Citizen Records" dev tool (CitizenRecordAdminController),
 * standing in for the real National Population Register.
 */
@Entity
@Table(name = "citizen_photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitizenPhoto {

    @Id
    @Column(name = "sa_id_number", length = 13)
    private String saIdNumber;

    @Lob
    @Column(name = "photo", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] photo;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
