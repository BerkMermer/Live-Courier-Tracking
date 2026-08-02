package com.berk.courier_tracking_api.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/** Validates JWT on STOMP CONNECT and attaches the principal to the WebSocket session. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("STOMP CONNECT: Authorization header eksik — bağlantı anonim kalacak, " +
                        "korumalı topic'lere subscribe denemesi WebSocketSecurity tarafından reddedilecek");
                return message;
            }

            try {
                String jwt = authHeader.substring(7);
                String email = jwtService.extractEmail(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    accessor.setUser(authToken);
                    log.debug("STOMP CONNECT: kullanıcı doğrulandı, email={}", email);
                } else {
                    log.warn("STOMP CONNECT: geçersiz/süresi dolmuş JWT — bağlantı anonim kalacak");
                }
            } catch (Exception e) {
                log.warn("STOMP CONNECT: JWT doğrulama hatası: {}", e.getMessage());
            }
        }

        return message;
    }
}
