package com.berk.courier_tracking_api.controller;

import com.berk.courier_tracking_api.dto.CourierLocationResponse;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.CourierStatus;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.config.AppConfig;
import com.berk.courier_tracking_api.security.JwtAuthenticationFilter;
import com.berk.courier_tracking_api.security.UserPrincipal;
import com.berk.courier_tracking_api.service.CourierProfileService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CourierController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import({MethodSecurityTestConfig.class, AppConfig.class})
class CourierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourierProfileService courierProfileService;

    private UserPrincipal principal(UserRole role, Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("Test " + role.name());
        user.setRole(role);
        return UserPrincipal.from(user);
    }

    private CourierLocationResponse sampleLocationResponse(Long courierId) {
        return new CourierLocationResponse(
                courierId, "Mehmet Kurye", CourierStatus.AVAILABLE, 40.9909, 29.0303, LocalDateTime.now());
    }

    @Test
    void updateLocation_asCourier_shouldReturn200() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(40.9909, 29.0303);
        when(courierProfileService.updateLocation(eq(10L), any(LocationUpdateRequest.class)))
                .thenReturn(sampleLocationResponse(1L));

        mockMvc.perform(put("/api/v1/couriers/location")
                        .with(user(principal(UserRole.COURIER, 10L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(1));
    }

    @Test
    void updateLocation_asCustomer_shouldReturn403Forbidden() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(40.9909, 29.0303);

        mockMvc.perform(put("/api/v1/couriers/location")
                        .with(user(principal(UserRole.CUSTOMER, 1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCourierLocation_asCustomer_shouldReturn200() throws Exception {
        when(courierProfileService.getLocationById(5L)).thenReturn(sampleLocationResponse(5L));

        mockMvc.perform(get("/api/v1/couriers/5/location")
                        .with(user(principal(UserRole.CUSTOMER, 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(5));
    }

    @Test
    void getCourierLocation_asAdmin_shouldReturn200() throws Exception {
        when(courierProfileService.getLocationById(5L)).thenReturn(sampleLocationResponse(5L));

        mockMvc.perform(get("/api/v1/couriers/5/location")
                        .with(user(principal(UserRole.ADMIN, 99L))))
                .andExpect(status().isOk());
    }

    @Test
    void getCourierLocation_asCourier_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/5/location")
                        .with(user(principal(UserRole.COURIER, 10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyLocation_asCourier_shouldReturn200WithOwnLocation() throws Exception {
        when(courierProfileService.getMyLocation(10L)).thenReturn(sampleLocationResponse(1L));

        mockMvc.perform(get("/api/v1/couriers/me/location")
                        .with(user(principal(UserRole.COURIER, 10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(1));
    }

    @Test
    void getMyLocation_asCustomer_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me/location")
                        .with(user(principal(UserRole.CUSTOMER, 1L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyLocation_asAdmin_shouldReturn403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/me/location")
                        .with(user(principal(UserRole.ADMIN, 99L))))
                .andExpect(status().isForbidden());
    }
}
