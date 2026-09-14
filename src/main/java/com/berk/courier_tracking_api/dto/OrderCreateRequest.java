package com.berk.courier_tracking_api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(

        @NotBlank(message = "Alış adresi boş olamaz")
        String pickupAddress,

        @NotNull(message = "Alış noktası enlemi boş olamaz")
        @DecimalMin(value = "-90.0", message = "Enlem -90 ile 90 arasında olmalıdır")
        @DecimalMax(value = "90.0", message = "Enlem -90 ile 90 arasında olmalıdır")
        Double pickupLatitude,

        @NotNull(message = "Alış noktası boylamı boş olamaz")
        @DecimalMin(value = "-180.0", message = "Boylam -180 ile 180 arasında olmalıdır")
        @DecimalMax(value = "180.0", message = "Boylam -180 ile 180 arasında olmalıdır")
        Double pickupLongitude,

        @NotBlank(message = "Teslimat adresi boş olamaz")
        String deliveryAddress

) {}
