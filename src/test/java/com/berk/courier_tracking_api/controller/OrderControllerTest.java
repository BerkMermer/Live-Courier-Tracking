package com.berk.courier_tracking_api.controller;

import com.berk.courier_tracking_api.dto.OrderCreateRequest;
import com.berk.courier_tracking_api.dto.OrderResponse;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.OrderStatus;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.config.AppConfig;
import com.berk.courier_tracking_api.security.JwtAuthenticationFilter;
import com.berk.courier_tracking_api.security.UserPrincipal;
import com.berk.courier_tracking_api.service.OrderService;
import com.berk.courier_tracking_api.support.MethodSecurityTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OrderController.class,
        // Jwt filter needs JwtService/UserDetailsService absent from this slice; auth is tested via @PreAuthorize.
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import({MethodSecurityTestConfig.class, AppConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private UserPrincipal principal(UserRole role, Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("Test " + role.name());
        user.setRole(role);
        return UserPrincipal.from(user);
    }

    private OrderResponse sampleOrderResponse() {
        return new OrderResponse(
                1L,
                "TRK-1", "Kadıköy, İstanbul", 40.99, 29.03, "Beşiktaş, İstanbul",
                OrderStatus.PENDING, "Ayşe Müşteri", null, null, LocalDateTime.now());
    }

    @Test
    void createOrder_asCustomer_shouldReturn201Created() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest("Kadıköy, İstanbul", 40.99, 29.03, "Beşiktaş, İstanbul");
        when(orderService.createOrder(any(OrderCreateRequest.class), anyLong())).thenReturn(sampleOrderResponse());

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(principal(UserRole.CUSTOMER, 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-1"));
    }

    @Test
    void createOrder_asCourier_shouldReturn403Forbidden() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest("Kadıköy, İstanbul", 40.99, 29.03, "Beşiktaş, İstanbul");

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(principal(UserRole.COURIER, 2L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOrder_withBlankPickupAddress_shouldReturn400BadRequest() throws Exception {
        String invalidJson = """
                {"pickupAddress": "", "pickupLatitude": 40.99, "pickupLongitude": 29.03, "deliveryAddress": "Beşiktaş"}
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(principal(UserRole.CUSTOMER, 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyOrders_asCustomer_shouldReturn200WithOwnOrders() throws Exception {
        when(orderService.getOrdersByCustomer(1L)).thenReturn(List.of(sampleOrderResponse()));

        mockMvc.perform(get("/api/v1/orders/me")
                        .with(user(principal(UserRole.CUSTOMER, 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value("TRK-1"));
    }

    @Test
    void getMyOrders_asCourier_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")
                        .with(user(principal(UserRole.COURIER, 2L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOrder_asAdmin_shouldReturn200() throws Exception {
        when(orderService.getOrderById(eq(1L), any())).thenReturn(sampleOrderResponse());

        mockMvc.perform(get("/api/v1/orders/1")
                        .with(user(principal(UserRole.ADMIN, 99L))))
                .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_asCustomer_shouldReturn200() throws Exception {
        when(orderService.cancelOrder(eq(1L), any())).thenReturn(sampleOrderResponse());

        mockMvc.perform(post("/api/v1/orders/1/cancel")
                        .with(user(principal(UserRole.CUSTOMER, 1L))))
                .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_asCourier_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders/1/cancel")
                        .with(user(principal(UserRole.COURIER, 2L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignCourier_asAdmin_shouldReturn200() throws Exception {
        when(orderService.assignCourierToOrder(1L)).thenReturn(sampleOrderResponse());

        mockMvc.perform(post("/api/v1/orders/1/assign-courier")
                        .with(user(principal(UserRole.ADMIN, 99L))))
                .andExpect(status().isOk());
    }

    @Test
    void assignCourier_asCustomer_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/orders/1/assign-courier")
                        .with(user(principal(UserRole.CUSTOMER, 1L))))
                .andExpect(status().isForbidden());
    }
}
