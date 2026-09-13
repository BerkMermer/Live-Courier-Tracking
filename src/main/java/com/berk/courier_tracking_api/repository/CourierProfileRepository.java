package com.berk.courier_tracking_api.repository;

import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.enums.CourierStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, Long> {

    List<CourierProfile> findByStatus(CourierStatus status);

    @Query("SELECT c.id FROM CourierProfile c WHERE c.status = :status")
    List<Long> findIdsByStatus(@Param("status") CourierStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CourierProfile c WHERE c.id = :id")
    Optional<CourierProfile> findByIdForUpdate(@Param("id") Long id);

    Optional<CourierProfile> findByUser_Id(Long userId);

    List<CourierProfile> findByLastKnownLatIsNotNullAndLastKnownLngIsNotNull();
}
