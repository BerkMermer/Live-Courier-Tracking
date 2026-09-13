package com.berk.courier_tracking_api.repository;

import com.berk.courier_tracking_api.entity.Order;
import com.berk.courier_tracking_api.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Optional<Order> findByTrackingNumber(String trackingNumber);

    List<Order> findByCustomer_Id(Long customerId);

    List<Order> findByCourier_Id(Long courierId);

    List<Order> findByStatus(OrderStatus status);

    boolean existsByCustomer_IdAndCourier_IdAndStatusIn(
            Long customerId,
            Long courierId,
            Collection<OrderStatus> statuses
    );
}
