package com.berk.courier_tracking_api.repository;

import com.berk.courier_tracking_api.entity.Order;
import com.berk.courier_tracking_api.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByTrackingNumber(String trackingNumber);

    List<Order> findByCustomer_Id(Long customerId);

    List<Order> findByCourier_Id(Long courierId);

    List<Order> findByStatus(OrderStatus status);

    boolean existsByCustomer_IdAndCourier_Id(Long customerId, Long courierId);
}
