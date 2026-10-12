package com.dha.identity.repository;

import com.dha.identity.entity.CitizenPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface CitizenPhotoRepository extends JpaRepository<CitizenPhoto, String> {

    // For the admin list's "has photo" column without loading any image bytes.
    @Query("select p.saIdNumber from CitizenPhoto p")
    Set<String> findAllSaIdNumbers();
}
