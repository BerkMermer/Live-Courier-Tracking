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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CourierAssignmentConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final double PICKUP_LAT = 40.9909;
    private static final double PICKUP_LNG = 29.0303;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void assignCourier_whenTwoOrdersRaceForOneCourier_onlyOneWins() throws Exception {
        AuthResponse customer = registerCustomer();
        AuthResponse courier = registerCourier();
        CourierLocationResponse location = updateCourierLocation(courier.token(), PICKUP_LAT, PICKUP_LNG);

        OrderResponse order1 = createOrder(customer.token());
        OrderResponse order2 = createOrder(customer.token());

        List<MvcResult> results = assignInParallel(courier.token(), order1.id(), order2.id());
        int statusA = results.get(0).getResponse().getStatus();
        int statusB = results.get(1).getResponse().getStatus();

        assertEquals(1, countStatus(statusA, statusB, 200),
                "Exactly one assign request should succeed, got " + statusA + " and " + statusB);
        assertEquals(1, countStatus(statusA, statusB, 409),
                "The losing assign request should be 409, got " + statusA + " and " + statusB);

        OrderResponse after1 = getOrder(customer.token(), order1.id());
        OrderResponse after2 = getOrder(customer.token(), order2.id());

        long assignedCount = List.of(after1, after2).stream()
                .filter(order -> order.status() == OrderStatus.ASSIGNED)
                .count();
        long pendingCount = List.of(after1, after2).stream()
                .filter(order -> order.status() == OrderStatus.PENDING)
                .count();

        assertEquals(1, assignedCount);
        assertEquals(1, pendingCount);

        OrderResponse assigned = after1.status() == OrderStatus.ASSIGNED ? after1 : after2;
        OrderResponse pending = after1.status() == OrderStatus.PENDING ? after1 : after2;
        assertEquals(location.courierId(), assigned.courierId());
        assertNull(pending.courierId());
        assertNotEquals(assigned.id(), pending.id());

        CourierLocationResponse courierAfter = getMyLocation(courier.token());
        assertEquals(CourierStatus.ON_DELIVERY, courierAfter.status());
    }

    @Test
    void assignCourier_whenSameOrderRequestedInParallel_secondGetsConflict() throws Exception {
        AuthResponse customer = registerCustomer();
        AuthResponse courier = registerCourier();
        updateCourierLocation(courier.token(), PICKUP_LAT, PICKUP_LNG);
        OrderResponse order = createOrder(customer.token());

        List<MvcResult> results = assignInParallel(courier.token(), order.id(), order.id());
        int statusA = results.get(0).getResponse().getStatus();
        int statusB = results.get(1).getResponse().getStatus();

        assertEquals(1, countStatus(statusA, statusB, 200),
                "Exactly one assign request should succeed, got " + statusA + " and " + statusB);
        assertEquals(1, countStatus(statusA, statusB, 409),
                "The duplicate assign should be 409, got " + statusA + " and " + statusB);

        OrderResponse after = getOrder(customer.token(), order.id());
        assertEquals(OrderStatus.ASSIGNED, after.status());
        assertNotNull(after.courierId());
    }

    private List<MvcResult> assignInParallel(String courierToken, Long orderIdA, Long orderIdB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MvcResult> first = pool.submit(() -> assignOnce(courierToken, orderIdA, ready, start));
            Future<MvcResult> second = pool.submit(() -> assignOnce(courierToken, orderIdB, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();

            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            pool.shutdownNow();
        }
    }

    private MvcResult assignOnce(
            String courierToken,
            Long orderId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start assign-courier");
        }
        return mockMvc.perform(post("/api/v1/orders/" + orderId + "/assign-courier")
                        .header(HttpHeaders.AUTHORIZATION, bearer(courierToken)))
                .andReturn();
    }

    private int countStatus(int statusA, int statusB, int expected) {
        int count = 0;
        if (statusA == expected) {
            count++;
        }
        if (statusB == expected) {
            count++;
        }
        return count;
    }

    private AuthResponse registerCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserRegisterRequest request = new UserRegisterRequest(
                "Race Customer",
                "race-customer-" + suffix + "@example.com",
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
                "Race Courier",
                "race-courier-" + suffix + "@example.com",
                uniquePhone(),
                "securePass123",
                "34 RAC " + suffix.substring(0, 3).toUpperCase()
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
