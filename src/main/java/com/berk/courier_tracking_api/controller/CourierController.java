package com.berk.courier_tracking_api.controller;

import com.berk.courier_tracking_api.dto.CourierLocationResponse;
import com.berk.courier_tracking_api.dto.LocationUpdateRequest;
import com.berk.courier_tracking_api.security.UserPrincipal;
import com.berk.courier_tracking_api.service.CourierProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/couriers")
@RequiredArgsConstructor
public class CourierController {

    private final CourierProfileService courierProfileService;

    @PutMapping("/location")
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<CourierLocationResponse> updateLocation(
            @Valid @RequestBody LocationUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CourierLocationResponse response =
                courierProfileService.updateLocation(principal.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/location")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ResponseEntity<CourierLocationResponse> getCourierLocation(@PathVariable Long id) {
        CourierLocationResponse response = courierProfileService.getLocationById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/location")
    @PreAuthorize("hasRole('COURIER')")
    public ResponseEntity<CourierLocationResponse> getMyLocation(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CourierLocationResponse response = courierProfileService.getMyLocation(principal.getId());
        return ResponseEntity.ok(response);
    }
}
