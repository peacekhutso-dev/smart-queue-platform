package com.dha.catalog.repository;

import com.dha.catalog.entity.ApplicantType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicantTypeRepository extends JpaRepository<ApplicantType, Long> {
    Optional<ApplicantType> findByCode(String code);
    List<ApplicantType> findByActiveTrueOrderByDisplayOrderAsc();
}
