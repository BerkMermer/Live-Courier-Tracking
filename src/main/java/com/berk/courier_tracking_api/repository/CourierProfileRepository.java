package com.berk.courier_tracking_api.repository;

import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.enums.CourierStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, Long> {

    List<CourierProfile> findByStatus(CourierStatus status);

    Optional<CourierProfile> findByUser_Id(Long userId);

    List<CourierProfile> findByLastKnownLatIsNotNullAndLastKnownLngIsNotNull();
}
