package com.synchat.backend.realtime.websocket;

import com.synchat.backend.realtime.websocket.security.WebSocketAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketGatewayHandler webSocketGatewayHandler;
    private final WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;

    public WebSocketConfig(
            WebSocketGatewayHandler webSocketGatewayHandler,
            WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor
    ) {
        this.webSocketGatewayHandler = webSocketGatewayHandler;
        this.webSocketAuthHandshakeInterceptor = webSocketAuthHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketGatewayHandler, "/ws")
                .addInterceptors(webSocketAuthHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
