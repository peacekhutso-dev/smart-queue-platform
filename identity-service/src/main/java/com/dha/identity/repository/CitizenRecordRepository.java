package com.dha.identity.repository;

import com.dha.identity.entity.CitizenRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CitizenRecordRepository extends JpaRepository<CitizenRecord, String> {
    // saIdNumber is the @Id — findById(saIdNumber) is all login confirmation needs.
}
