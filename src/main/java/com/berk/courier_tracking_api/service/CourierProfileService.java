package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.CourierLocationResponse;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.security.UserPrincipal;

public interface CourierProfileService {

    CourierLocationResponse updateLocation(Long courierUserId, LocationUpdateRequest request);

    CourierLocationResponse getLocationById(Long courierId, UserPrincipal principal);

    /** courierUserId is User.id (JWT), not CourierProfile.id. */
    CourierLocationResponse getMyLocation(Long courierUserId);
}
