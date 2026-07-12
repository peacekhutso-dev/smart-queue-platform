package com.dha.catalog.repository;

import com.dha.catalog.entity.DocumentRequirement;
import com.dha.catalog.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {
    List<DocumentRequirement> findByServiceOfferingOrderByDisplayOrderAsc(ServiceOffering serviceOffering);
    List<DocumentRequirement> findByServiceOfferingInOrderByDisplayOrderAsc(List<ServiceOffering> serviceOfferings);
}
