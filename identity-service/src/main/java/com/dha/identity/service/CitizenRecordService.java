package com.dha.identity.service;

import com.dha.identity.dto.request.CitizenRecordRequest;
import com.dha.identity.dto.response.CitizenRecordResponse;
import com.dha.identity.entity.CitizenPhoto;
import com.dha.identity.entity.CitizenRecord;
import com.dha.identity.repository.CitizenPhotoRepository;
import com.dha.identity.repository.CitizenRecordRepository;
import com.dha.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * DEV ONLY: lets an admin load Home Affairs citizen records and their ID
 * photos into this prototype's stand-in for the National Population
 * Register. In a real deployment this data would come from DHA's own
 * systems, never from an upload screen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CitizenRecordService {

    private static final long MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5MB
    private static final List<String> PHOTO_TYPES = List.of("image/jpeg", "image/png");

    private final CitizenRecordRepository citizenRecordRepository;
    private final CitizenPhotoRepository citizenPhotoRepository;
    private final UserRepository userRepository;

    public List<CitizenRecordResponse> list() {
        Set<String> withPhoto = citizenPhotoRepository.findAllSaIdNumbers();
        return citizenRecordRepository.findAll().stream()
                .sorted(Comparator.comparing(CitizenRecord::getLastName, String.CASE_INSENSITIVE_ORDER))
                .map(r -> CitizenRecordResponse.from(r, withPhoto.contains(r.getSaIdNumber())))
                .toList();
    }

    @Transactional
    public CitizenRecordResponse save(CitizenRecordRequest req) {
        CitizenRecord record = citizenRecordRepository.findById(req.getSaIdNumber())
                .orElseGet(() -> CitizenRecord.builder().saIdNumber(req.getSaIdNumber()).build());
        record.setFirstName(req.getFirstName().trim());
        record.setMiddleName(req.getMiddleName());
        record.setLastName(req.getLastName().trim());
        record.setDateOfBirth(req.getDateOfBirth());
        record.setGender(req.getGender());
        record.setNationality(req.getNationality());
        record.setCitizenshipStatus(req.getCitizenshipStatus());
        record.setPhoneNumber(req.getPhoneNumber());
        record.setEmail(req.getEmail());
        record.setResidentialAddress(req.getResidentialAddress());
        citizenRecordRepository.save(record);
        log.info("DEV citizen record saved saIdNumber={}", req.getSaIdNumber());
        return CitizenRecordResponse.from(record, citizenPhotoRepository.existsById(req.getSaIdNumber()));
    }

    @Transactional
    public void savePhoto(String saIdNumber, MultipartFile file) throws IOException {
        if (!citizenRecordRepository.existsById(saIdNumber)) {
            throw new IllegalArgumentException("No citizen record for SA ID " + saIdNumber);
        }
        if (file.isEmpty()) throw new IllegalArgumentException("Photo is empty");
        if (file.getSize() > MAX_PHOTO_BYTES) throw new IllegalArgumentException("Photo exceeds 5MB");
        String contentType = file.getContentType();
        if (contentType == null || !PHOTO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Photo must be a JPG or PNG");
        }
        citizenPhotoRepository.save(CitizenPhoto.builder()
                .saIdNumber(saIdNumber)
                .photo(file.getBytes())
                .contentType(contentType)
                .build());
        log.info("DEV citizen photo saved saIdNumber={} bytes={}", saIdNumber, file.getSize());
    }

    public Optional<CitizenPhoto> getPhoto(String saIdNumber) {
        return citizenPhotoRepository.findById(saIdNumber);
    }

    @Transactional
    public void deletePhoto(String saIdNumber) {
        citizenPhotoRepository.deleteById(saIdNumber);
    }

    /** The Home Affairs photo for the SA ID on this user's account, if any. */
    public Optional<CitizenPhoto> getPhotoForUser(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> u.getSaIdNumber())
                .flatMap(citizenPhotoRepository::findById);
    }
}
