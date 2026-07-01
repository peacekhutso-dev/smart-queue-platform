package com.dha.queue.repository;

import com.dha.queue.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByIsActiveTrue();

    List<Branch> findByProvinceAndIsActiveTrue(String province);

    @Query(value = """
        SELECT * FROM branch b
        WHERE (
            6371 * acos(
                cos(radians(:latitude))
                * cos(radians(b.latitude))
                * cos(radians(b.longitude) - radians(:longitude))
                + sin(radians(:latitude))
                * sin(radians(b.latitude))
            )
        ) <= :radius
        LIMIT :limit
        """, nativeQuery = true)
    List<Branch> findNearbyBranches(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radius") double radius,
            @Param("limit") int limit
    );
}