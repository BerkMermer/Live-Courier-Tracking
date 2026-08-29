package com.berk.courier_tracking_api.dto;

import com.berk.courier_tracking_api.entity.Order;
import com.berk.courier_tracking_api.enums.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String trackingNumber,
        String pickupAddress,
        Double pickupLatitude,
        Double pickupLongitude,
        String deliveryAddress,
        OrderStatus status,
        String customerName,
        Long courierId,
        String courierName,
        LocalDateTime createdAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTrackingNumber(),
                order.getPickupAddress(),
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDeliveryAddress(),
                order.getStatus(),
                order.getCustomer().getFullName(),
                order.getCourier() != null ? order.getCourier().getId() : null,
                order.getCourier() != null && order.getCourier().getUser() != null
                        ? order.getCourier().getUser().getFullName()
                        : null,
                order.getCreatedAt()
        );
    }
}
