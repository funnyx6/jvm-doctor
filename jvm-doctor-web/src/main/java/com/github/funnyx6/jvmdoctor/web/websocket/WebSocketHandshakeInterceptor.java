package com.github.funnyx6.jvmdoctor.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 用于从 URL 参数中获取 appId
 */
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandshakeInterceptor.class);
    
    public static final String APP_ID_KEY = "appId";
    
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            
            // 从 URL 参数获取 appId
            String appId = servletRequest.getServletRequest().getParameter("appId");
            
            // 如果有 appId 参数，添加到 attributes 中
            if (appId != null && !appId.isEmpty()) {
                try {
                    Long parsedAppId = Long.parseLong(appId);
                    attributes.put(APP_ID_KEY, parsedAppId);
                    logger.debug("WebSocket handshake: appId = {}", parsedAppId);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid appId parameter: {}", appId);
                }
            }
        }
        
        return true;
    }
    
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            logger.error("WebSocket handshake failed", exception);
        }
    }
}
