package com.synchat.backend.realtime.websocket.security;

import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.security.AuthPrincipal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeInterceptor.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final WebSocketAuthService webSocketAuthService;

    public WebSocketAuthHandshakeInterceptor(WebSocketAuthService webSocketAuthService) {
        this.webSocketAuthService = webSocketAuthService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            String accessToken = accessToken(request);
            UUID requestedDeviceId = deviceId(request.getURI());
            AuthPrincipal principal = webSocketAuthService.authenticate(accessToken, requestedDeviceId);

            attributes.put(WebSocketAuthAttributes.AUTH_PRINCIPAL, principal);
            attributes.put(WebSocketAuthAttributes.USER_ID, principal.userId());
            attributes.put(WebSocketAuthAttributes.SESSION_ID, principal.sessionId());
            attributes.put(WebSocketAuthAttributes.DEVICE_ID, principal.deviceId());

            return true;
        } catch (AppException | IllegalArgumentException exception) {
            log.warn(
                    "WebSocketAuthHandshakeInterceptor.beforeHandshake exception commandType=handshake requestId=null socketId=null userId=null",
                    exception
            );
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private String accessToken(ServerHttpRequest request) {
        String queryToken = queryParam(request.getURI(), "accessToken");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken;
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private UUID deviceId(URI uri) {
        String value = queryParam(uri, "deviceId");
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private String queryParam(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
    }
}
