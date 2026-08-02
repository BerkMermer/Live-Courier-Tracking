package com.berk.courier_tracking_api.dto;

import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.enums.CourierStatus;

import java.time.LocalDateTime;

public record CourierLocationResponse(
        Long courierId,
        String fullName,
        CourierStatus status,
        Double latitude,
        Double longitude,
        LocalDateTime lastLocationUpdate
) {

    public static CourierLocationResponse from(CourierProfile profile) {
        return new CourierLocationResponse(
                profile.getId(),
                profile.getUser().getFullName(),
                profile.getStatus(),
                profile.getLastKnownLat(),
                profile.getLastKnownLng(),
                profile.getLastLocationUpdate()
        );
    }
}
