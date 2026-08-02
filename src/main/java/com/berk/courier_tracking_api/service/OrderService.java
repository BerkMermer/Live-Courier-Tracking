package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.OrderCreateRequest;
import com.berk.courier_tracking_api.dto.OrderResponse;
import com.berk.courier_tracking_api.security.UserPrincipal;

import java.util.List;

public interface OrderService {

    /** customerId comes from JWT, not the request body (BOLA prevention). */
    OrderResponse createOrder(OrderCreateRequest request, Long customerId);

    OrderResponse getOrderById(Long orderId, UserPrincipal principal);

    List<OrderResponse> getOrdersByCustomer(Long customerId);

    OrderResponse cancelOrder(Long orderId, UserPrincipal principal);

    OrderResponse assignCourierToOrder(Long orderId);
}
