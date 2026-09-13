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
        String courierVehiclePlate,
        String courierPhoneMasked,
        LocalDateTime createdAt
) {

    public static OrderResponse from(Order order) {
        var courier = order.getCourier();
        return new OrderResponse(
                order.getId(),
                order.getTrackingNumber(),
                order.getPickupAddress(),
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                order.getDeliveryAddress(),
                order.getStatus(),
                order.getCustomer().getFullName(),
                courier != null ? courier.getId() : null,
                courier != null && courier.getUser() != null
                        ? courier.getUser().getFullName()
                        : null,
                courier != null ? courier.getVehiclePlate() : null,
                courier != null ? maskPhone(courier.getPhoneNumber()) : null,
                order.getCreatedAt()
        );
    }

    private static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        String digits = phoneNumber.replaceAll("\\D", "");
        String localDigits = digits.startsWith("90") && digits.length() == 12
                ? digits.substring(2)
                : digits;

        if (localDigits.length() < 3) {
            return "***";
        }

        String lastTwoDigits = localDigits.substring(localDigits.length() - 2);
        if (localDigits.length() == 10) {
            return "+90 " + localDigits.charAt(0) + "** *** ** " + lastTwoDigits;
        }

        return "*** ** " + lastTwoDigits;
    }
}
