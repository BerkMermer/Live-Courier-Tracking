package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.CourierLocationResponse;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.enums.OrderStatus;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.exception.ResourceNotFoundException;
import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.repository.OrderRepository;
import com.berk.courier_tracking_api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourierProfileServiceImpl implements CourierProfileService {

    private final CourierProfileRepository courierProfileRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisLocationService redisLocationService;

    // RabbitMQ STOMP rejects nested /topic paths; use dots as segment separators.
    private static final String COURIER_LOCATION_TOPIC_PREFIX = "/topic/courier-location.";

    @Override
    @Transactional
    public CourierLocationResponse updateLocation(Long courierUserId, LocationUpdateRequest request) {
        CourierProfile courier = courierProfileRepository.findByUser_Id(courierUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kurye profili bulunamadı, user id: " + courierUserId));

        courier.setLastKnownLat(request.latitude());
        courier.setLastKnownLng(request.longitude());
        courier.setLastLocationUpdate(LocalDateTime.now());

        CourierProfile updatedCourier; updatedCourier = courierProfileRepository.save(courier);
        CourierLocationResponse response = CourierLocationResponse.from(updatedCourier);

        // Also writes Redis GEO and publishes WebSocket so clients see the live location.
        redisLocationService.addCourierLocation(
                updatedCourier.getId(),
                request.latitude(),
                request.longitude()
        );

        messagingTemplate.convertAndSend(
                COURIER_LOCATION_TOPIC_PREFIX + updatedCourier.getId(),
                response
        );

        return response;
    }

    @Override
    public CourierLocationResponse getLocationById(Long courierId, UserPrincipal principal) {
        if (principal.getRole() == UserRole.CUSTOMER
                && !orderRepository.existsByCustomer_IdAndCourier_IdAndStatusIn(
                principal.getId(), courierId, OrderStatus.liveTracking())) {
            throw new AccessDeniedException("Bu kuryenin konumunu görme yetkiniz yok");
        }

        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kurye profili bulunamadı, id: " + courierId));

        return CourierLocationResponse.from(courier);
    }

    @Override
    public CourierLocationResponse getMyLocation(Long courierUserId) {
        CourierProfile courier = courierProfileRepository.findByUser_Id(courierUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Kurye profili bulunamadı, user id: " + courierUserId));

        return CourierLocationResponse.from(courier);
    }
}
