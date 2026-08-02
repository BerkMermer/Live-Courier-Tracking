package com.berk.courier_tracking_api.config;

import com.berk.courier_tracking_api.security.WebSocketAuthInterceptor;
import com.berk.courier_tracking_api.security.WebSocketSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP over WebSocket with an external RabbitMQ broker relay for scalable location fan-out. */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketSecurity webSocketSecurity;

    @Value("${rabbitmq.host}")
    private String relayHost;

    @Value("${rabbitmq.stomp-port}")
    private int relayPort;

    @Value("${rabbitmq.login}")
    private String relayLogin;

    @Value("${rabbitmq.passcode}")
    private String relayPasscode;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-courier")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Docker hostname'i vhost sanmasın diye "/" zorunlu
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setVirtualHost("/")
                .setClientLogin(relayLogin)
                .setClientPasscode(relayPasscode)
                .setSystemLogin(relayLogin)
                .setSystemPasscode(relayPasscode);

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor, webSocketSecurity);
    }
}
