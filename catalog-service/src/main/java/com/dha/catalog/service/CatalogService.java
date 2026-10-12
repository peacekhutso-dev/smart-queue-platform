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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final ApplicantTypeRepository applicantTypeRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final DocumentRequirementRepository documentRequirementRepository;

    // Batches the whole catalog into 3 queries total (applicant types,
    // services for all of them, documents for all of those services)
    // instead of 1 + one-per-applicant-type + one-per-service — this method
    // is what backs the frontend's full-catalog load on every page visit.
    public CatalogResponse getFullCatalog() {
        List<ApplicantType> applicantTypes = applicantTypeRepository.findByActiveTrueOrderByDisplayOrderAsc();

        List<ServiceOffering> allServices = serviceOfferingRepository
                .findByApplicantTypeInAndActiveTrueOrderByDisplayOrderAsc(applicantTypes);
        Map<Long, List<ServiceOffering>> servicesByApplicantType = allServices.stream()
                .collect(Collectors.groupingBy(s -> s.getApplicantType().getId()));

        List<DocumentRequirement> allDocuments = documentRequirementRepository
                .findByServiceOfferingInOrderByDisplayOrderAsc(allServices);
        Map<Long, List<DocumentRequirement>> documentsByService = allDocuments.stream()
                .collect(Collectors.groupingBy(d -> d.getServiceOffering().getId()));

        Function<ServiceOffering, ServiceOfferingResponse> mapService = service ->
                mapServiceWithDocuments(service, documentsByService.getOrDefault(service.getId(), List.of()));

        List<ApplicantTypeResponse> response = applicantTypes.stream()
                .map(at -> mapApplicantType(at,
                        servicesByApplicantType.getOrDefault(at.getId(), List.of()).stream()
                                .map(mapService)
                                .toList()))
                .toList();

        return CatalogResponse.builder().applicantTypes(response).build();
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
        return mapServiceWithDocuments(service, documentRequirementRepository
                .findByServiceOfferingOrderByDisplayOrderAsc(service));
    }

    private ServiceOfferingResponse mapServiceWithDocuments(ServiceOffering service, List<DocumentRequirement> documentEntities) {
        List<DocumentRequirementResponse> documents = documentEntities.stream()
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
