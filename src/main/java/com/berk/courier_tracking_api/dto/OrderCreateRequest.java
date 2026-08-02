package com.berk.courier_tracking_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(

        @NotBlank(message = "Alış adresi boş olamaz")
        String pickupAddress,

        @NotNull(message = "Alış noktası enlemi boş olamaz")
        Double pickupLatitude,

        @NotNull(message = "Alış noktası boylamı boş olamaz")
        Double pickupLongitude,

        @NotBlank(message = "Teslimat adresi boş olamaz")
        String deliveryAddress

) {}
