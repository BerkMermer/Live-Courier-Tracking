package com.berk.courier_tracking_api.controller;

import com.berk.courier_tracking_api.dto.AuthResponse;
import com.berk.courier_tracking_api.dto.CourierLocationResponse;
import com.berk.courier_tracking_api.dto.CourierRegisterRequest;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.dto.OrderCreateRequest;
import com.berk.courier_tracking_api.dto.OrderResponse;
import com.berk.courier_tracking_api.dto.UserRegisterRequest;
import com.berk.courier_tracking_api.enums.CourierStatus;
import com.berk.courier_tracking_api.enums.OrderStatus;
import com.berk.courier_tracking_api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderLifecycleIntegrationTest extends IntegrationTestBase {

    private static final double PICKUP_LAT = 40.9909;
    private static final double PICKUP_LNG = 29.0303;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void assignCourier_whenCourierHasNoRedisGeo_shouldReturnConflict() throws Exception {
        AuthResponse customer = registerCustomer();
        AuthResponse courier = registerCourier();
        OrderResponse order = createOrder(customer.token());

        mockMvc.perform(post("/api/v1/orders/" + order.id() + "/assign-courier")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courier.token())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Yakında Redis GEO kaydı olan müsait kurye yok (önce PUT /couriers/location)"));
    }

    @Test
    void fullFlow_registerLocationAssignPickupDeliver_shouldComplete() throws Exception {
        AuthResponse customer = registerCustomer();
        AuthResponse courier = registerCourier();

        CourierLocationResponse location = updateCourierLocation(courier.token(), PICKUP_LAT, PICKUP_LNG);
        assertEquals(CourierStatus.AVAILABLE, location.status());
        assertNotNull(location.courierId());

        OrderResponse created = createOrder(customer.token());
        assertEquals(OrderStatus.PENDING, created.status());
        assertNull(created.courierId());

        OrderResponse assigned = assignCourier(courier.token(), created.id());
        assertEquals(OrderStatus.ASSIGNED, assigned.status());
        assertEquals(location.courierId(), assigned.courierId());
        assertEquals(courier.user().fullName(), assigned.courierName());
        assertTrue(assigned.courierVehiclePlate().startsWith("34 LIF "));
        assertTrue(assigned.courierPhoneMasked().matches("\\+90 \\d\\*\\* \\*\\*\\* \\*\\* \\d{2}"));

        OrderResponse pickedUp = pickup(courier.token(), created.id());
        assertEquals(OrderStatus.PICKED_UP, pickedUp.status());

        OrderResponse delivered = deliver(courier.token(), created.id());
        assertEquals(OrderStatus.DELIVERED, delivered.status());

        OrderResponse asCustomer = getOrder(customer.token(), created.id());
        assertEquals(OrderStatus.DELIVERED, asCustomer.status());
        assertEquals(location.courierId(), asCustomer.courierId());

        CourierLocationResponse afterDelivery = getMyLocation(courier.token());
        assertEquals(CourierStatus.AVAILABLE, afterDelivery.status());
        assertEquals(location.courierId(), afterDelivery.courierId());
    }

    private AuthResponse registerCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserRegisterRequest request = new UserRegisterRequest(
                "Lifecycle Customer",
                "customer-" + suffix + "@example.com",
                uniquePhone(),
                "securePass123"
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, AuthResponse.class);
    }

    private AuthResponse registerCourier() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CourierRegisterRequest request = new CourierRegisterRequest(
                "Lifecycle Courier",
                "courier-" + suffix + "@example.com",
                uniquePhone(),
                "securePass123",
                "34 LIF " + suffix.substring(0, 3).toUpperCase()
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-courier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("COURIER"))
                .andReturn();
        return read(result, AuthResponse.class);
    }

    private CourierLocationResponse updateCourierLocation(String token, double lat, double lng) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/couriers/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LocationUpdateRequest(lat, lng))))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, CourierLocationResponse.class);
    }

    private OrderResponse createOrder(String customerToken) throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                "Kadıköy, İstanbul",
                PICKUP_LAT,
                PICKUP_LNG,
                "Beşiktaş, İstanbul"
        );
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, OrderResponse.class);
    }

    private OrderResponse assignCourier(String courierToken, Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/" + orderId + "/assign-courier")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierToken)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, OrderResponse.class);
    }

    private OrderResponse pickup(String courierToken, Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/" + orderId + "/pickup")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierToken)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, OrderResponse.class);
    }

    private OrderResponse deliver(String courierToken, Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/" + orderId + "/deliver")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierToken)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, OrderResponse.class);
    }

    private OrderResponse getOrder(String token, Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, OrderResponse.class);
    }

    private CourierLocationResponse getMyLocation(String courierToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/couriers/me/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierToken)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, CourierLocationResponse.class);
    }

    private <T> T read(MvcResult result, Class<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), type);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniquePhone() {
        long n = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_0000L);
        return String.format("+90%010d", n);
    }
}
