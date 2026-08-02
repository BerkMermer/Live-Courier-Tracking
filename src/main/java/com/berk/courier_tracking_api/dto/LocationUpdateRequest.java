package com.berk.courier_tracking_api.dto;

import jakarta.validation.constraints.NotNull;

public record LocationUpdateRequest(

        @NotNull(message = "Enlem (latitude) boş olamaz")
        Double latitude,

        @NotNull(message = "Boylam (longitude) boş olamaz")
        Double longitude

) {}
