package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.CourierLocationResponse;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.CourierStatus;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.exception.ResourceNotFoundException;
import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.repository.OrderRepository;
import com.berk.courier_tracking_api.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierProfileServiceImplTest {

    @Mock
    private CourierProfileRepository courierProfileRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RedisLocationService redisLocationService;

    @InjectMocks
    private CourierProfileServiceImpl courierProfileService;

    private CourierProfile buildCourier(Long courierId, Long userId, String fullName) {
        User user = new User();
        user.setId(userId);
        user.setFullName(fullName);

        CourierProfile courier = new CourierProfile();
        courier.setId(courierId);
        courier.setUser(user);
        courier.setStatus(CourierStatus.AVAILABLE);
        return courier;
    }

    @Test
    void updateLocation_whenCourierExists_shouldUpdateRedisAndBroadcastAndReturnResponse() {
        Long courierUserId = 10L;
        CourierProfile courier = buildCourier(1L, courierUserId, "Test Kurye");
        LocationUpdateRequest request = new LocationUpdateRequest(40.9909, 29.0303);

        when(courierProfileRepository.findByUser_Id(courierUserId)).thenReturn(Optional.of(courier));
        when(courierProfileRepository.save(any(CourierProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierLocationResponse response = courierProfileService.updateLocation(courierUserId, request);

        assertNotNull(response);
        assertEquals(1L, response.courierId());
        assertEquals(40.9909, response.latitude());
        assertEquals(29.0303, response.longitude());

        verify(redisLocationService).addCourierLocation(1L, 40.9909, 29.0303);
        verify(messagingTemplate).convertAndSend(eq("/topic/courier-location.1"), any(CourierLocationResponse.class));
    }

    @Test
    void updateLocation_whenCourierNotFound_shouldThrowResourceNotFoundException() {
        when(courierProfileRepository.findByUser_Id(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> courierProfileService.updateLocation(99L, new LocationUpdateRequest(1.0, 1.0)));
    }

    private UserPrincipal principal(UserRole role, Long userId) {
        User user = new User();
        user.setId(userId);
        user.setEmail("user" + userId + "@example.com");
        user.setFullName("Test");
        user.setRole(role);
        return UserPrincipal.from(user);
    }

    @Test
    void getLocationById_whenAdmin_shouldReturnResponse() {
        CourierProfile courier = buildCourier(5L, 50L, "Test Kurye");
        when(courierProfileRepository.findById(5L)).thenReturn(Optional.of(courier));

        CourierLocationResponse response = courierProfileService.getLocationById(5L, principal(UserRole.ADMIN, 99L));

        assertEquals(5L, response.courierId());
        assertEquals("Test Kurye", response.fullName());
    }

    @Test
    void getLocationById_whenCustomerHasActiveOrder_shouldReturnResponse() {
        CourierProfile courier = buildCourier(5L, 50L, "Test Kurye");
        UserPrincipal customer = principal(UserRole.CUSTOMER, 1L);
        when(orderRepository.existsByCustomer_IdAndCourier_IdAndStatusIn(
                eq(1L), eq(5L), any())).thenReturn(true);
        when(courierProfileRepository.findById(5L)).thenReturn(Optional.of(courier));

        CourierLocationResponse response = courierProfileService.getLocationById(5L, customer);

        assertEquals(5L, response.courierId());
    }

    @Test
    void getLocationById_whenCustomerHasNoActiveOrder_shouldThrowAccessDenied() {
        UserPrincipal customer = principal(UserRole.CUSTOMER, 1L);
        when(orderRepository.existsByCustomer_IdAndCourier_IdAndStatusIn(
                eq(1L), eq(5L), any())).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> courierProfileService.getLocationById(5L, customer));
    }

    @Test
    void getLocationById_whenCourierNotFound_shouldThrowResourceNotFoundException() {
        when(courierProfileRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> courierProfileService.getLocationById(1L, principal(UserRole.ADMIN, 99L)));
    }

    @Test
    void getMyLocation_whenCourierExists_shouldReturnResponseByUserId() {
        CourierProfile courier = buildCourier(7L, 70L, "Kurye Yedi");
        when(courierProfileRepository.findByUser_Id(70L)).thenReturn(Optional.of(courier));

        CourierLocationResponse response = courierProfileService.getMyLocation(70L);

        assertEquals(7L, response.courierId());
        assertEquals("Kurye Yedi", response.fullName());
    }

    @Test
    void getMyLocation_whenCourierNotFound_shouldThrowResourceNotFoundException() {
        when(courierProfileRepository.findByUser_Id(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> courierProfileService.getMyLocation(1L));
    }
}
