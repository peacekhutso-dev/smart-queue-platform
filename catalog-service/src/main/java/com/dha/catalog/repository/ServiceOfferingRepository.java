package com.dha.catalog.repository;

import com.dha.catalog.entity.ApplicantType;
import com.dha.catalog.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    Optional<ServiceOffering> findByCode(String code);
    List<ServiceOffering> findByApplicantTypeAndActiveTrueOrderByDisplayOrderAsc(ApplicantType applicantType);
    List<ServiceOffering> findByActiveTrueOrderByDisplayOrderAsc();
}
