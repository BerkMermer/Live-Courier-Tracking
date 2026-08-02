package com.berk.courier_tracking_api.security;

import com.berk.courier_tracking_api.repository.CourierProfileRepository;
import com.berk.courier_tracking_api.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Authorizes STOMP SUBSCRIBE frames for courier-location topics using the JWT principal. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSecurity implements ChannelInterceptor {

    private static final Pattern COURIER_LOCATION_TOPIC = Pattern.compile("^/topic/courier-location\\.(\\d+)$");

    private final OrderRepository orderRepository;
    private final CourierProfileRepository courierProfileRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = org.springframework.messaging.support.MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();

        if (destination != null && !isAuthorized(destination, principal)) {
            log.warn("WebSocket SUBSCRIBE reddedildi: destination={}, user={}",
                    destination, principal != null ? principal.getName() : "anonim");
            return null;
        }

        return message;
    }

    @EventListener
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        log.info("WebSocket subscribe onaylandı: destination={}, user={}",
                accessor.getDestination(),
                event.getUser() != null ? event.getUser().getName() : "anonim");
    }

    private boolean isAuthorized(String destination, Principal principal) {
        Matcher matcher = COURIER_LOCATION_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return true;
        }

        if (!(principal instanceof UsernamePasswordAuthenticationToken authToken)
                || !(authToken.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            return false;
        }

        Long courierId = Long.parseLong(matcher.group(1));

        return switch (userPrincipal.getRole()) {
            case ADMIN -> true;
            case COURIER -> courierProfileRepository.findByUser_Id(userPrincipal.getId())
                    .map(courier -> courier.getId().equals(courierId))
                    .orElse(false);
            case CUSTOMER -> orderRepository.existsByCustomer_IdAndCourier_Id(userPrincipal.getId(), courierId);
        };
    }
}
