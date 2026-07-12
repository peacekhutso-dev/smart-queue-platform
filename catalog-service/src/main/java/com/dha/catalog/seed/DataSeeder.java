package com.dha.catalog.seed;

import com.dha.catalog.entity.ApplicantType;
import com.dha.catalog.entity.DocumentRequirement;
import com.dha.catalog.entity.ServiceOffering;
import com.dha.catalog.entity.ServiceOffering.FeeType;
import com.dha.catalog.repository.ApplicantTypeRepository;
import com.dha.catalog.repository.DocumentRequirementRepository;
import com.dha.catalog.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds the catalog from the official DHA fee schedule captured in
 * docs/decisions/home-affairs-service-catalog.md. Runs once — every row
 * created here is admin-editable afterward via CatalogAdminController, this
 * is just the initial data load, not a hardcoded source of truth.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ApplicantTypeRepository applicantTypeRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final DocumentRequirementRepository documentRequirementRepository;

    @Override
    public void run(String... args) {
        if (applicantTypeRepository.count() > 0) {
            log.info("Catalog already seeded, skipping.");
            return;
        }

        ApplicantType citizen = applicantTypeRepository.save(applicantType(
                "SA_CITIZEN", "South African Citizen", "South African citizens applying for civic and travel documents.", 1));
        ApplicantType permanentResident = applicantTypeRepository.save(applicantType(
                "PERMANENT_RESIDENT", "Permanent Resident", "Holders of South African permanent residence status.", 2));
        ApplicantType foreignNational = applicantTypeRepository.save(applicantType(
                "FOREIGN_NATIONAL", "Foreign National", "Visa and permit applications for non-SA citizens.", 3));
        ApplicantType refugee = applicantTypeRepository.save(applicantType(
                "REFUGEE_ASYLUM_SEEKER", "Refugee / Asylum Seeker", "Refugee status and asylum seeker documentation.", 4));

        log.warn("PERMANENT_RESIDENT seeded with zero services — the source fee schedule " +
                "(docs/decisions/home-affairs-service-catalog.md) has no fee table for permanent " +
                "residents, only citizens/foreign nationals/refugees. Add services via the admin " +
                "API once the real PR fee schedule is sourced; do not guess at fees.");

        seedCitizenServices(citizen);
        seedForeignNationalServices(foreignNational);
        seedRefugeeServices(refugee);

        log.info("Catalog seeded: 4 applicant types, {} services, {} document requirements.",
                serviceOfferingRepository.count(), documentRequirementRepository.count());
    }

    private void seedCitizenServices(ApplicantType citizen) {
        int order = 1;

        ServiceOffering smartIdFirst = save(free(citizen, "SMART_ID_FIRST", "First Smart ID Card (16+)", order++));
        ServiceOffering smartIdReplacement = save(fixed(citizen, "SMART_ID_REPLACEMENT", "Smart ID Replacement", 140, order++));
        save(fixed(citizen, "TIC", "Temporary Identity Certificate (TIC)", 70, order++));
        ServiceOffering passportAdultTourist = save(fixed(citizen, "PASSPORT_ADULT_TOURIST", "Adult Tourist Passport (32 pages)", 600, order++));
        save(fixed(citizen, "PASSPORT_ADULT_MAXI", "Adult Maxi Passport (48 pages)", 1200, order++));
        save(fixed(citizen, "PASSPORT_CHILD", "Child Passport", 600, order++));
        save(fixed(citizen, "EMERGENCY_TRAVEL_CERTIFICATE", "Emergency Travel Certificate", 140, order++));
        save(free(citizen, "BIRTH_REGISTRATION", "Birth Registration", order++));
        save(free(citizen, "BIRTH_CERTIFICATE_FIRST_ISSUE", "Birth Certificate (First Issue)", order++));
        save(fixed(citizen, "BIRTH_CERTIFICATE_DUPLICATE", "Birth Certificate (Duplicate)", 75, order++));
        save(free(citizen, "MARRIAGE_REGISTRATION", "Marriage Registration", order++));
        save(fixed(citizen, "MARRIAGE_CERTIFICATE_DUPLICATE", "Marriage Certificate (Duplicate)", 75, order++));
        save(free(citizen, "DEATH_REGISTRATION", "Death Registration", order++));
        save(fixed(citizen, "DEATH_CERTIFICATE_DUPLICATE", "Death Certificate (Duplicate)", 75, order++));
        save(range(citizen, "AMENDMENT_PERSONAL_PARTICULARS", "Amendment of Personal Particulars", 70, 140, order++));
        save(fixed(citizen, "RETENTION_SA_CITIZENSHIP", "Retention of SA Citizenship", 300, order++));
        save(fixed(citizen, "REINSTATEMENT_SA_CITIZENSHIP", "Reinstatement of SA Citizenship", 300, order++));
        save(free(citizen, "RENUNCIATION_SA_CITIZENSHIP", "Renunciation of SA Citizenship", order));

        seedFirstSmartIdDocuments(smartIdFirst);
        seedSmartIdReplacementDocuments(smartIdReplacement);
        seedAdultPassportDocuments(passportAdultTourist);
    }

    private void seedForeignNationalServices(ApplicantType foreignNational) {
        String vfsNote = "Excludes applicable VFS Global service charges";
        int order = 1;

        save(fixedWithNote(foreignNational, "VISITOR_VISA", "Visitor Visa", 425, vfsNote, order++));
        ServiceOffering studyVisa = save(fixedWithNote(foreignNational, "STUDY_VISA", "Study Visa", 425, vfsNote, order++));
        save(fixedWithNote(foreignNational, "WORK_VISA", "Work Visa", 425, vfsNote, order++));
        save(fixedWithNote(foreignNational, "CRITICAL_SKILLS_VISA", "Critical Skills Visa", 425, vfsNote, order++));
        save(fixedWithNote(foreignNational, "BUSINESS_VISA", "Business Visa", 425, vfsNote, order++));
        save(fixedWithNote(foreignNational, "RELATIVE_VISA", "Relative Visa", 425, vfsNote, order++));
        ServiceOffering permanentResidencePermit = save(fixedWithNote(foreignNational, "PERMANENT_RESIDENCE_PERMIT", "Permanent Residence Permit", 1520, vfsNote, order++));
        save(varies(foreignNational, "VISA_RENEWAL", "Visa Renewal", vfsNote, order++));
        save(varies(foreignNational, "VISA_EXTENSION", "Visa Extension", vfsNote, order));

        seedStudyVisaDocuments(studyVisa);
        seedPermanentResidencePermitDocuments(permanentResidencePermit);
    }

    private void seedRefugeeServices(ApplicantType refugee) {
        int order = 1;
        save(free(refugee, "REFUGEE_STATUS_APPLICATION", "Refugee Status Application", order++));
        save(free(refugee, "ASYLUM_SEEKER_PERMIT", "Asylum Seeker Permit", order++));
        save(free(refugee, "REFUGEE_PERMIT_RENEWAL", "Permit Renewal", order++));
        save(free(refugee, "REFUGEE_IDENTITY_DOCUMENT", "Refugee Identity Document", order++));
        save(free(refugee, "REFUGEE_TRAVEL_DOCUMENT", "Refugee Travel Document", order));
    }

    private void seedFirstSmartIdDocuments(ServiceOffering service) {
        int order = 1;
        doc(service, "BIRTH_CERTIFICATE", "Birth Certificate", true, null, order++);
        doc(service, "PROOF_OF_RESIDENCE", "Proof of Residence", true, null, order);
    }

    private void seedSmartIdReplacementDocuments(ServiceOffering service) {
        int order = 1;
        doc(service, "SA_SMART_ID", "Existing ID (if available)", false, "if available", order++);
        doc(service, "AFFIDAVIT", "Affidavit", false, "if lost or stolen", order++);
        doc(service, "PROOF_OF_RESIDENCE", "Proof of Residence", true, null, order);
    }

    private void seedAdultPassportDocuments(ServiceOffering service) {
        int order = 1;
        doc(service, "SA_SMART_ID", "South African Smart ID", true, null, order++);
        doc(service, "PASSPORT", "Existing Passport", false, "for renewals", order++);
        doc(service, "OTHER", "Passport Photo", false, "if required", order);
    }

    private void seedStudyVisaDocuments(ServiceOffering service) {
        int order = 1;
        doc(service, "FOREIGN_PASSPORT", "Foreign Passport", true, null, order++);
        doc(service, "OTHER", "University Acceptance Letter", true, null, order++);
        doc(service, "MEDICAL_REPORT", "Proof of Medical Cover", true, null, order++);
        doc(service, "BANK_STATEMENT", "Financial Proof", true, null, order++);
        doc(service, "POLICE_CLEARANCE_CERTIFICATE", "Police Clearance Certificate", true, null, order);
    }

    private void seedPermanentResidencePermitDocuments(ServiceOffering service) {
        int order = 1;
        doc(service, "FOREIGN_PASSPORT", "Passport", true, null, order++);
        doc(service, "VISA_DOCUMENTS", "Current Visa", true, null, order++);
        doc(service, "POLICE_CLEARANCE_CERTIFICATE", "Police Clearance Certificate", true, null, order++);
        doc(service, "MEDICAL_REPORT", "Medical Report", true, null, order++);
        doc(service, "BANK_STATEMENT", "Financial Statements", true, null, order);
    }

    private ApplicantType applicantType(String code, String displayName, String description, int order) {
        return ApplicantType.builder()
                .code(code).displayName(displayName).description(description)
                .displayOrder(order).active(true).build();
    }

    private ServiceOffering free(ApplicantType applicantType, String code, String name, int order) {
        return ServiceOffering.builder()
                .applicantType(applicantType).code(code).name(name)
                .feeType(FeeType.FREE).currency("ZAR")
                .displayOrder(order).active(true).build();
    }

    private ServiceOffering fixed(ApplicantType applicantType, String code, String name, int amount, int order) {
        return fixedWithNote(applicantType, code, name, amount, null, order);
    }

    private ServiceOffering fixedWithNote(ApplicantType applicantType, String code, String name, int amount, String note, int order) {
        BigDecimal fee = BigDecimal.valueOf(amount);
        return ServiceOffering.builder()
                .applicantType(applicantType).code(code).name(name)
                .feeType(FeeType.FIXED).feeMin(fee).feeMax(fee).currency("ZAR").feeNote(note)
                .displayOrder(order).active(true).build();
    }

    private ServiceOffering range(ApplicantType applicantType, String code, String name, int min, int max, int order) {
        return ServiceOffering.builder()
                .applicantType(applicantType).code(code).name(name)
                .feeType(FeeType.RANGE).feeMin(BigDecimal.valueOf(min)).feeMax(BigDecimal.valueOf(max)).currency("ZAR")
                .displayOrder(order).active(true).build();
    }

    private ServiceOffering varies(ApplicantType applicantType, String code, String name, String note, int order) {
        return ServiceOffering.builder()
                .applicantType(applicantType).code(code).name(name)
                .feeType(FeeType.VARIES).currency("ZAR").feeNote(note)
                .displayOrder(order).active(true).build();
    }

    private ServiceOffering save(ServiceOffering service) {
        return serviceOfferingRepository.save(service);
    }

    private void doc(ServiceOffering service, String documentCategory, String label, boolean required, String conditionNote, int order) {
        documentRequirementRepository.save(DocumentRequirement.builder()
                .serviceOffering(service)
                .documentCategory(documentCategory)
                .label(label)
                .required(required)
                .conditionNote(conditionNote)
                .displayOrder(order)
                .build());
    }
}
