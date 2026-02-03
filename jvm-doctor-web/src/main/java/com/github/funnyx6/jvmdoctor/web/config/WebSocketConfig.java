package com.github.funnyx6.jvmdoctor.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.github.funnyx6.jvmdoctor.web.websocket.MetricsWebSocketHandler;
import com.github.funnyx6.jvmdoctor.web.websocket.WebSocketHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final MetricsWebSocketHandler metricsHandler;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    
    public WebSocketConfig(MetricsWebSocketHandler metricsHandler, 
                           WebSocketHandshakeInterceptor handshakeInterceptor) {
        this.metricsHandler = metricsHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(metricsHandler, "/ws/metrics")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
