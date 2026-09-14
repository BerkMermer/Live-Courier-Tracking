package com.berk.courier_tracking_api.security;

import com.berk.courier_tracking_api.dto.AuthResponse;
import com.berk.courier_tracking_api.dto.CourierRegisterRequest;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.dto.OrderCreateRequest;
import com.berk.courier_tracking_api.dto.OrderResponse;
import com.berk.courier_tracking_api.dto.UserRegisterRequest;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.repository.UserRepository;
import com.berk.courier_tracking_api.service.CourierProfileService;
import com.berk.courier_tracking_api.service.OrderService;
import com.berk.courier_tracking_api.service.UserService;
import com.berk.courier_tracking_api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class WebSocketSecurityIntegrationTest extends IntegrationTestBase {

    private static final double PICKUP_LAT = 40.9909;
    private static final double PICKUP_LNG = 29.0303;

    @Autowired
    private WebSocketAuthInterceptor authInterceptor;

    @Autowired
    private WebSocketSecurity webSocketSecurity;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CourierProfileService courierProfileService;

    @Autowired
    private UserRepository userRepository;

    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connect_withValidJwt_shouldAttachAuthenticatedPrincipal() {
        AuthResponse customer = registerCustomer();
        Message<?> authenticated = authInterceptor.preSend(
                connectMessage("Bearer " + customer.token()),
                channel
        );

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                authenticated,
                StompHeaderAccessor.class
        );
        assertNotNull(accessor);
        assertNotNull(accessor.getUser());
        assertEquals(customer.user().email(), accessor.getUser().getName());
    }

    @Test
    void connect_withInvalidJwt_shouldRemainAnonymous() {
        Message<?> result = authInterceptor.preSend(
                connectMessage("Bearer invalid-token"),
                channel
        );

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                result,
                StompHeaderAccessor.class
        );
        assertNotNull(accessor);
        assertNull(accessor.getUser());
    }

    @Test
    void subscribe_customerWithActiveOrder_shouldBeAllowed() {
        AuthResponse customer = registerCustomer();
        AuthResponse courier = registerCourier();
        courierProfileService.updateLocation(
                courier.user().id(),
                new LocationUpdateRequest(PICKUP_LAT, PICKUP_LNG)
        );
        OrderResponse order = orderService.createOrder(
                new OrderCreateRequest("Kadıköy", PICKUP_LAT, PICKUP_LNG, "Beşiktaş"),
                customer.user().id()
        );
        OrderResponse assigned = orderService.assignCourierToOrder(order.id());

        Message<?> result = webSocketSecurity.preSend(
                subscribeMessage(
                        "/topic/courier-location." + assigned.courierId(),
                        principal(customer.user().id())
                ),
                channel
        );

        assertNotNull(result);
    }

    @Test
    void subscribe_customerWithoutActiveOrder_shouldBeDenied() {
        AuthResponse customer = registerCustomer();
        AuthResponse courier = registerCourier();
        Long courierId = courierProfileService.getMyLocation(courier.user().id()).courierId();

        Message<?> result = webSocketSecurity.preSend(
                subscribeMessage(
                        "/topic/courier-location." + courierId,
                        principal(customer.user().id())
                ),
                channel
        );

        assertNull(result);
    }

    @Test
    void subscribe_courierToAnotherCourierTopic_shouldBeDenied() {
        AuthResponse firstCourier = registerCourier();
        AuthResponse secondCourier = registerCourier();
        Long secondCourierId = courierProfileService.getMyLocation(secondCourier.user().id()).courierId();

        Message<?> result = webSocketSecurity.preSend(
                subscribeMessage(
                        "/topic/courier-location." + secondCourierId,
                        principal(firstCourier.user().id())
                ),
                channel
        );

        assertNull(result);
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, UserPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        ));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UserPrincipal principal(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return UserPrincipal.from(user);
    }

    private AuthResponse registerCustomer() {
        String suffix = suffix();
        return userService.registerUser(new UserRegisterRequest(
                "WebSocket Customer",
                "ws-customer-" + suffix + "@example.com",
                uniquePhone(),
                "securePass123"
        ));
    }

    private AuthResponse registerCourier() {
        String suffix = suffix();
        return userService.registerCourier(new CourierRegisterRequest(
                "WebSocket Courier",
                "ws-courier-" + suffix + "@example.com",
                uniquePhone(),
                "securePass123",
                "34 WS " + suffix.substring(0, 3).toUpperCase()
        ));
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String uniquePhone() {
        long number = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_0000L);
        return String.format("+90%010d", number);
    }
}
