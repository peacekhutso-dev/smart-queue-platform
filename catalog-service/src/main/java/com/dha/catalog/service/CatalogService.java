package com.dha.catalog.service;

import com.dha.catalog.dto.response.ApplicantTypeResponse;
import com.dha.catalog.dto.response.CatalogResponse;
import com.dha.catalog.dto.response.DocumentRequirementResponse;
import com.dha.catalog.dto.response.ServiceOfferingResponse;
import com.dha.catalog.entity.ApplicantType;
import com.dha.catalog.entity.DocumentRequirement;
import com.dha.catalog.entity.ServiceOffering;
import com.dha.catalog.repository.ApplicantTypeRepository;
import com.dha.catalog.repository.DocumentRequirementRepository;
import com.dha.catalog.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final ApplicantTypeRepository applicantTypeRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final DocumentRequirementRepository documentRequirementRepository;

    public CatalogResponse getFullCatalog() {
        List<ApplicantTypeResponse> applicantTypes = applicantTypeRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::mapApplicantTypeWithServices)
                .toList();
        return CatalogResponse.builder().applicantTypes(applicantTypes).build();
    }

    public List<ApplicantTypeResponse> getApplicantTypes() {
        return applicantTypeRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(at -> mapApplicantType(at, null))
                .toList();
    }

    public List<ServiceOfferingResponse> getServicesForApplicantType(String applicantTypeCode) {
        ApplicantType applicantType = applicantTypeRepository.findByCode(applicantTypeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown applicant type: " + applicantTypeCode));
        return serviceOfferingRepository.findByApplicantTypeAndActiveTrueOrderByDisplayOrderAsc(applicantType)
                .stream()
                .map(this::mapServiceWithDocuments)
                .toList();
    }

    public ServiceOfferingResponse getServiceByCode(String code) {
        ServiceOffering service = serviceOfferingRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + code));
        return mapServiceWithDocuments(service);
    }

    public List<DocumentRequirementResponse> getDocumentsForService(String code) {
        ServiceOffering service = serviceOfferingRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown service: " + code));
        return documentRequirementRepository.findByServiceOfferingOrderByDisplayOrderAsc(service)
                .stream()
                .map(this::mapDocumentRequirement)
                .toList();
    }

    private ApplicantTypeResponse mapApplicantTypeWithServices(ApplicantType applicantType) {
        List<ServiceOfferingResponse> services = serviceOfferingRepository
                .findByApplicantTypeAndActiveTrueOrderByDisplayOrderAsc(applicantType)
                .stream()
                .map(this::mapServiceWithDocuments)
                .toList();
        return mapApplicantType(applicantType, services);
    }

    private ApplicantTypeResponse mapApplicantType(ApplicantType applicantType, List<ServiceOfferingResponse> services) {
        return ApplicantTypeResponse.builder()
                .id(applicantType.getId())
                .code(applicantType.getCode())
                .displayName(applicantType.getDisplayName())
                .description(applicantType.getDescription())
                .services(services)
                .build();
    }

    private ServiceOfferingResponse mapServiceWithDocuments(ServiceOffering service) {
        List<DocumentRequirementResponse> documents = documentRequirementRepository
                .findByServiceOfferingOrderByDisplayOrderAsc(service)
                .stream()
                .map(this::mapDocumentRequirement)
                .toList();

        return ServiceOfferingResponse.builder()
                .id(service.getId())
                .applicantTypeCode(service.getApplicantType().getCode())
                .code(service.getCode())
                .name(service.getName())
                .description(service.getDescription())
                .feeType(service.getFeeType())
                .feeMin(service.getFeeMin())
                .feeMax(service.getFeeMax())
                .currency(service.getCurrency())
                .feeNote(service.getFeeNote())
                .feeDisplay(formatFeeDisplay(service))
                .estimatedTurnaround(service.getEstimatedTurnaround())
                .documents(documents)
                .build();
    }

    private DocumentRequirementResponse mapDocumentRequirement(DocumentRequirement requirement) {
        return DocumentRequirementResponse.builder()
                .id(requirement.getId())
                .documentCategory(requirement.getDocumentCategory())
                .label(requirement.getLabel())
                .required(requirement.getRequired())
                .conditionNote(requirement.getConditionNote())
                .build();
    }

    private String formatFeeDisplay(ServiceOffering service) {
        return switch (service.getFeeType()) {
            case FREE -> "Free";
            case VARIES -> "Varies";
            case FIXED -> formatAmount(service.getFeeMin());
            case RANGE -> formatAmount(service.getFeeMin()) + "–" + formatAmount(service.getFeeMax());
        };
    }

    private String formatAmount(BigDecimal amount) {
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        boolean whole = normalized.stripTrailingZeros().scale() <= 0;
        if (whole) {
            String digits = normalized.toBigInteger().toString();
            StringBuilder grouped = new StringBuilder();
            int count = 0;
            for (int i = digits.length() - 1; i >= 0; i--) {
                grouped.append(digits.charAt(i));
                count++;
                if (count % 3 == 0 && i != 0) grouped.append(' ');
            }
            return "R" + grouped.reverse();
        }
        return "R" + normalized.toPlainString();
    }
}
