package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.OrderCreateRequest;
import com.berk.courier_tracking_api.dto.OrderResponse;
import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.entity.Order;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.CourierStatus;
import com.berk.courier_tracking_api.enums.OrderStatus;
import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.repository.OrderRepository;
import com.berk.courier_tracking_api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourierProfileRepository courierProfileRepository;

    @Mock
    private RedisLocationService redisLocationService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void createOrder_WhenValidRequest_ShouldReturnOrderResponse() {
        Long customerId = 1L;
        OrderCreateRequest request = new OrderCreateRequest(
                "Kadıköy, İstanbul",
                40.9909,
                29.0303,
                "Beşiktaş, İstanbul"
        );

        User customer = new User();
        customer.setId(customerId);
        customer.setFullName("Berk Mermer");

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            order.setTrackingNumber(UUID.randomUUID().toString());
            order.setCreatedAt(LocalDateTime.of(2026, 7, 21, 10, 0));
            return order;
        });

        OrderResponse response = orderService.createOrder(request, customerId);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertNotNull(response.trackingNumber());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals("Kadıköy, İstanbul", response.pickupAddress());
        assertEquals("Beşiktaş, İstanbul", response.deliveryAddress());
        assertEquals("Berk Mermer", response.customerName());
        assertNull(response.courierName());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order persistedOrder = orderCaptor.getValue();
        assertEquals(customer, persistedOrder.getCustomer());
        assertEquals(OrderStatus.PENDING, persistedOrder.getStatus());
        assertEquals(request.pickupLatitude(), persistedOrder.getPickupLatitude());
        assertEquals(request.pickupLongitude(), persistedOrder.getPickupLongitude());
    }

    @Test
    void assignCourierToOrder_WhenNoCourierAvailable_ShouldThrowException() {
        Long orderId = 42L;
        Order pendingOrder = new Order();
        pendingOrder.setId(orderId);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setPickupLatitude(41.0082);
        pendingOrder.setPickupLongitude(28.9784);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        when(courierProfileRepository.findByStatus(CourierStatus.AVAILABLE)).thenReturn(List.of());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.assignCourierToOrder(orderId)
        );

        assertEquals("Şu anda müsait kurye bulunmamaktadır", exception.getMessage());
        verify(orderRepository).findById(orderId);
        verify(courierProfileRepository).findByStatus(CourierStatus.AVAILABLE);
    }

    @Test
    void assignCourierToOrder_WhenRedisReturnsNearbyAvailable_ShouldAssignThatCourier() {
        User customer = new User();
        customer.setId(1L);
        customer.setFullName("Berk Mermer");

        Long orderId = 42L;
        Order pendingOrder = new Order();
        pendingOrder.setId(orderId);
        pendingOrder.setCustomer(customer);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setPickupLatitude(40.9909);
        pendingOrder.setPickupLongitude(29.0303);

        User courierUser = new User();
        courierUser.setId(10L);
        courierUser.setFullName("Ahmet Kurye");

        CourierProfile nearby = new CourierProfile();
        nearby.setId(7L);
        nearby.setUser(courierUser);
        nearby.setStatus(CourierStatus.AVAILABLE);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        when(courierProfileRepository.findByStatus(CourierStatus.AVAILABLE)).thenReturn(List.of(nearby));
        when(redisLocationService.findNearbyCouriers(40.9909, 29.0303)).thenReturn(List.of(7L));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.assignCourierToOrder(orderId);

        assertEquals(OrderStatus.ASSIGNED, response.status());
        assertEquals(7L, response.courierId());
        assertEquals(CourierStatus.ON_DELIVERY, nearby.getStatus());
    }

    @Test
    void assignCourierToOrder_WhenRedisHasNoNearbyCourier_ShouldThrowException() {
        Long orderId = 42L;
        Order pendingOrder = new Order();
        pendingOrder.setId(orderId);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setPickupLatitude(40.9909);
        pendingOrder.setPickupLongitude(29.0303);

        CourierProfile available = new CourierProfile();
        available.setId(7L);
        available.setStatus(CourierStatus.AVAILABLE);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        when(courierProfileRepository.findByStatus(CourierStatus.AVAILABLE)).thenReturn(List.of(available));
        when(redisLocationService.findNearbyCouriers(40.9909, 29.0303)).thenReturn(List.of());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> orderService.assignCourierToOrder(orderId)
        );

        assertEquals("Yakında Redis GEO kaydı olan müsait kurye yok (önce PUT /couriers/location)",
                exception.getMessage());
    }
}
