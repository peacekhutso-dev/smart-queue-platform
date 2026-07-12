package com.dha.branch.repository;

import com.dha.branch.entity.Branch;
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
        SELECT *, (
            6371 * acos(
                least(1, greatest(-1,
                    cos(radians(:latitude))
                    * cos(radians(b.latitude))
                    * cos(radians(b.longitude) - radians(:longitude))
                    + sin(radians(:latitude))
                    * sin(radians(b.latitude))
                ))
            )
        ) AS distance_km FROM branches b
        WHERE b.is_active = true
        HAVING distance_km <= :radius
        ORDER BY distance_km ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Branch> findNearbyBranches(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radius") double radius,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM branches b
        WHERE b.is_active = true
        ORDER BY (
            6371 * acos(
                least(1, greatest(-1,
                    cos(radians(:latitude))
                    * cos(radians(b.latitude))
                    * cos(radians(b.longitude) - radians(:longitude))
                    + sin(radians(:latitude))
                    * sin(radians(b.latitude))
                ))
            )
        ) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Branch> findClosestBranches(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("limit") int limit
    );
}
