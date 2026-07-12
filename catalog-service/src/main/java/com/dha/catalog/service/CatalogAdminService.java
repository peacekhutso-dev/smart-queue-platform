package com.dha.catalog.service;

import com.dha.catalog.dto.request.ApplicantTypeRequest;
import com.dha.catalog.dto.request.DocumentRequirementRequest;
import com.dha.catalog.dto.request.ServiceOfferingRequest;
import com.dha.catalog.entity.ApplicantType;
import com.dha.catalog.entity.DocumentRequirement;
import com.dha.catalog.entity.ServiceOffering;
import com.dha.catalog.entity.ServiceOffering.FeeType;
import com.dha.catalog.repository.ApplicantTypeRepository;
import com.dha.catalog.repository.DocumentRequirementRepository;
import com.dha.catalog.repository.ServiceOfferingRepository;
import com.dha.catalog.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogAdminService {

    private final ApplicantTypeRepository applicantTypeRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final DocumentRequirementRepository documentRequirementRepository;

    @Transactional
    public ApplicantType createApplicantType(ApplicantTypeRequest request, JwtPrincipal principal) {
        requireAdmin(principal);
        ApplicantType applicantType = ApplicantType.builder()
                .code(request.getCode())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder())
                .active(request.getActive() == null || request.getActive())
                .build();
        return applicantTypeRepository.save(applicantType);
    }

    @Transactional
    public ApplicantType updateApplicantType(Long id, ApplicantTypeRequest request, JwtPrincipal principal) {
        requireAdmin(principal);
        ApplicantType applicantType = applicantTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Applicant type not found: " + id));
        applicantType.setCode(request.getCode());
        applicantType.setDisplayName(request.getDisplayName());
        applicantType.setDescription(request.getDescription());
        applicantType.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) applicantType.setActive(request.getActive());
        return applicantTypeRepository.save(applicantType);
    }

    @Transactional
    public ServiceOffering createService(ServiceOfferingRequest request, JwtPrincipal principal) {
        requireAdmin(principal);
        validateFee(request.getFeeType(), request.getFeeMin(), request.getFeeMax());
        ApplicantType applicantType = applicantTypeRepository.findByCode(request.getApplicantTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown applicant type: " + request.getApplicantTypeCode()));

        ServiceOffering service = ServiceOffering.builder()
                .applicantType(applicantType)
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .feeType(request.getFeeType())
                .feeMin(request.getFeeMin())
                .feeMax(request.getFeeMax())
                .currency(request.getCurrency() != null ? request.getCurrency() : "ZAR")
                .feeNote(request.getFeeNote())
                .estimatedTurnaround(request.getEstimatedTurnaround())
                .displayOrder(request.getDisplayOrder())
                .active(request.getActive() == null || request.getActive())
                .build();
        return serviceOfferingRepository.save(service);
    }

    @Transactional
    public ServiceOffering updateService(Long id, ServiceOfferingRequest request, JwtPrincipal principal) {
        requireAdmin(principal);
        validateFee(request.getFeeType(), request.getFeeMin(), request.getFeeMax());
        ServiceOffering service = serviceOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        ApplicantType applicantType = applicantTypeRepository.findByCode(request.getApplicantTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown applicant type: " + request.getApplicantTypeCode()));

        service.setApplicantType(applicantType);
        service.setCode(request.getCode());
        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setFeeType(request.getFeeType());
        service.setFeeMin(request.getFeeMin());
        service.setFeeMax(request.getFeeMax());
        if (request.getCurrency() != null) service.setCurrency(request.getCurrency());
        service.setFeeNote(request.getFeeNote());
        service.setEstimatedTurnaround(request.getEstimatedTurnaround());
        service.setDisplayOrder(request.getDisplayOrder());
        if (request.getActive() != null) service.setActive(request.getActive());
        return serviceOfferingRepository.save(service);
    }

    @Transactional
    public void deactivateService(Long id, JwtPrincipal principal) {
        requireAdmin(principal);
        ServiceOffering service = serviceOfferingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + id));
        service.setActive(false);
        serviceOfferingRepository.save(service);
    }

    @Transactional
    public DocumentRequirement addDocumentRequirement(Long serviceId, DocumentRequirementRequest request, JwtPrincipal principal) {
        requireAdmin(principal);
        ServiceOffering service = serviceOfferingRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

        DocumentRequirement requirement = DocumentRequirement.builder()
                .serviceOffering(service)
                .documentCategory(request.getDocumentCategory())
                .label(request.getLabel())
                .required(request.getRequired() == null || request.getRequired())
                .conditionNote(request.getConditionNote())
                .displayOrder(request.getDisplayOrder())
                .build();
        return documentRequirementRepository.save(requirement);
    }

    @Transactional
    public DocumentRequirement updateDocumentRequirement(Long id, DocumentRequirementRequest request, JwtPrincipal principal) {
        requireAdmin(principal);
        DocumentRequirement requirement = documentRequirementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document requirement not found: " + id));
        requirement.setDocumentCategory(request.getDocumentCategory());
        requirement.setLabel(request.getLabel());
        if (request.getRequired() != null) requirement.setRequired(request.getRequired());
        requirement.setConditionNote(request.getConditionNote());
        requirement.setDisplayOrder(request.getDisplayOrder());
        return documentRequirementRepository.save(requirement);
    }

    @Transactional
    public void deleteDocumentRequirement(Long id, JwtPrincipal principal) {
        requireAdmin(principal);
        if (!documentRequirementRepository.existsById(id)) {
            throw new IllegalArgumentException("Document requirement not found: " + id);
        }
        documentRequirementRepository.deleteById(id);
    }

    private void requireAdmin(JwtPrincipal principal) {
        if (principal == null || !"ADMIN".equals(principal.role())) {
            throw new SecurityException("Admin role required");
        }
    }

    /**
     * FREE/VARIES carry no numeric amount; FIXED needs equal, non-null
     * min/max; RANGE needs min strictly less than max. Keeps "R70–R140" vs.
     * "R140" vs. "Free" vs. "Varies" representable without sentinel values.
     */
    private void validateFee(FeeType feeType, java.math.BigDecimal min, java.math.BigDecimal max) {
        switch (feeType) {
            case FREE, VARIES -> {
                if (min != null || max != null) {
                    throw new IllegalArgumentException(feeType + " fees must not specify feeMin/feeMax");
                }
            }
            case FIXED -> {
                if (min == null || max == null || min.compareTo(max) != 0) {
                    throw new IllegalArgumentException("FIXED fees require feeMin == feeMax, both non-null");
                }
            }
            case RANGE -> {
                if (min == null || max == null || min.compareTo(max) >= 0) {
                    throw new IllegalArgumentException("RANGE fees require feeMin < feeMax, both non-null");
                }
            }
        }
    }
}
