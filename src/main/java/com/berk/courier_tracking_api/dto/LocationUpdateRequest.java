package com.berk.courier_tracking_api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LocationUpdateRequest(

        @NotNull(message = "Enlem (latitude) boş olamaz")
        @DecimalMin(value = "-90.0", message = "Enlem -90 ile 90 arasında olmalıdır")
        @DecimalMax(value = "90.0", message = "Enlem -90 ile 90 arasında olmalıdır")
        Double latitude,

        @NotNull(message = "Boylam (longitude) boş olamaz")
        @DecimalMin(value = "-180.0", message = "Boylam -180 ile 180 arasında olmalıdır")
        @DecimalMax(value = "180.0", message = "Boylam -180 ile 180 arasında olmalıdır")
        Double longitude

) {}
