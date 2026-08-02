package com.berk.courier_tracking_api.service;

import com.berk.courier_tracking_api.dto.OrderCreateRequest;
import com.berk.courier_tracking_api.dto.OrderResponse;
import com.berk.courier_tracking_api.entity.CourierProfile;
import com.berk.courier_tracking_api.entity.Order;
import com.berk.courier_tracking_api.entity.User;
import com.berk.courier_tracking_api.enums.CourierStatus;
import com.berk.courier_tracking_api.enums.OrderStatus;
import com.berk.courier_tracking_api.enums.UserRole;
import com.berk.courier_tracking_api.exception.BusinessException;
import com.berk.courier_tracking_api.exception.ErrorCode;
import com.berk.courier_tracking_api.exception.ResourceNotFoundException;
import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.repository.OrderRepository;
import com.berk.courier_tracking_api.repository.UserRepository;
import com.berk.courier_tracking_api.security.UserPrincipal;
import com.berk.courier_tracking_api.service.RedisLocationService;
import com.berk.courier_tracking_api.util.HaversineUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final RedisLocationService redisLocationService;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, Long customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Müşteri bulunamadı, id: " + customerId));

        Order order = new Order();
        order.setCustomer(customer);
        order.setPickupAddress(request.pickupAddress());
        order.setPickupLatitude(request.pickupLatitude());
        order.setPickupLongitude(request.pickupLongitude());
        order.setDeliveryAddress(request.deliveryAddress());
        order.setStatus(OrderStatus.PENDING);

        Order savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder);
    }

    @Override
    public OrderResponse getOrderById(Long orderId, UserPrincipal principal) {
        Order order = findOrderOrThrow(orderId);
        validateOrderAccess(order, principal);
        return OrderResponse.from(order);
    }

    @Override
    public List<OrderResponse> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomer_Id(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, UserPrincipal principal) {
        Order order = findOrderOrThrow(orderId);
        validateCustomerOwnership(order, principal);

        if (order.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_TERMINAL,
                    "Tamamlanmış veya iptal edilmiş sipariş iptal edilemez");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE,
                    "Kurye atanmış sipariş iptal edilemez, mevcut durum: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);
        return OrderResponse.from(cancelledOrder);
    }

    @Override
    @Transactional
    public OrderResponse assignCourierToOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sipariş bulunamadı, id: " + orderId));

        if (order.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_TERMINAL,
                    "Tamamlanmış veya iptal edilmiş siparişe kurye atanamaz");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.COURIER_ALREADY_ASSIGNED,
                    "Bu siparişe zaten kurye atanmış, mevcut durum: " + order.getStatus());
        }

        List<CourierProfile> availableCouriers = courierProfileRepository.findByStatus(CourierStatus.AVAILABLE);
        if (availableCouriers.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_AVAILABLE_COURIER);
        }

        // Redis GEO first (nearest by distance); Haversine fallback if Redis empty/unavailable or no intersection.
        CourierProfile selectedCourier = findNearestAvailableCourier(
                order.getPickupLatitude(),
                order.getPickupLongitude(),
                availableCouriers
        );

        order.setCourier(selectedCourier);
        order.setStatus(OrderStatus.ASSIGNED);

        selectedCourier.setStatus(CourierStatus.ON_DELIVERY);
        // Managed entity: dirty-checked on commit — no explicit courierProfileRepository.save().

        Order updatedOrder = orderRepository.save(order);
        return OrderResponse.from(updatedOrder);
    }

    private CourierProfile findNearestAvailableCourier(double pickupLatitude, double pickupLongitude, List<CourierProfile> availableCouriers) {
        Map<Long, CourierProfile> availableById = availableCouriers.stream()
                .collect(Collectors.toMap(CourierProfile::getId, Function.identity()));

        List<Long> nearbyCourierIds = redisLocationService.findNearbyCouriers(pickupLatitude, pickupLongitude);

        for (Long courierId : nearbyCourierIds) {
            CourierProfile courier = availableById.get(courierId);
            if (courier != null) {
                return courier;
            }
        }

        return availableCouriers.stream()
                .filter(courier -> courier.getLastKnownLat() != null
                        && courier.getLastKnownLng() != null)
                .min(Comparator.comparingDouble(courier ->
                        HaversineUtility.calculateDistanceKm(
                                pickupLatitude,
                                pickupLongitude,
                                courier.getLastKnownLat(),
                                courier.getLastKnownLng()
                        )))
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_AVAILABLE_COURIER,
                        "Konum bilgisi olan müsait kurye bulunmamaktadır"));
    }

    private Order findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sipariş bulunamadı, id: " + orderId));
    }

    private void validateOrderAccess(Order order, UserPrincipal principal) {
        if (principal.getRole() == UserRole.ADMIN) {
            return;
        }

        if (principal.getRole() == UserRole.CUSTOMER) {
            validateCustomerOwnership(order, principal);
            return;
        }

        if (principal.getRole() == UserRole.COURIER) {
            validateCourierAssignment(order, principal);
            return;
        }

        throw new AccessDeniedException("Bu siparişe erişim yetkiniz bulunmamaktadır");
    }

    private void validateCustomerOwnership(Order order, UserPrincipal principal) {
        if (!order.getCustomer().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Bu siparişe erişim yetkiniz bulunmamaktadır");
        }
    }

    private void validateCourierAssignment(Order order, UserPrincipal principal) {
        if (order.getCourier() == null
                || !order.getCourier().getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Bu siparişe erişim yetkiniz bulunmamaktadır");
        }
    }
}
