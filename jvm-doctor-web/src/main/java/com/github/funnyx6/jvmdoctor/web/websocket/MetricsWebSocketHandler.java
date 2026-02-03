package com.github.funnyx6.jvmdoctor.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.funnyx6.jvmdoctor.web.websocket.WebSocketHandshakeInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标 WebSocket 处理器
 * 负责推送实时指标数据到客户端
 */
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    
    // 存储所有连接的会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // 存储 appId -> session 的映射（用于定向推送）
    private final Map<Long, WebSocketSession> appSessions = new ConcurrentHashMap<>();
    
    public MetricsWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        // 如果有 appId，添加到映射
        Long appId = (Long) session.getAttributes().get(WebSocketHandshakeInterceptor.APP_ID_KEY);
        if (appId != null) {
            appSessions.put(appId, session);
            logger.info("WebSocket connected: sessionId={}, appId={}", sessionId, appId);
        } else {
            logger.info("WebSocket connected: sessionId={} (dashboard)", sessionId);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        
        // 从 app 映射中移除
        Long appId = (Long) session.getAttributes().get(WebSocketHandshakeInterceptor.APP_ID_KEY);
        if (appId != null && appSessions.get(appId) == session) {
            appSessions.remove(appId);
            logger.info("WebSocket disconnected: sessionId={}, appId={}", sessionId, appId);
        } else {
            logger.info("WebSocket disconnected: sessionId={}", sessionId);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 客户端发送消息的处理（心跳、订阅等）
        String payload = message.getPayload();
        logger.debug("Received message from {}: {}", session.getId(), payload);
        
        // 简单的心跳响应
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("WebSocket transport error for session {}", session.getId(), exception);
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }
    
    /**
     * 推送指标数据到所有连接的客户端
     */
    public void broadcastMetrics(ObjectNode metrics) {
        String json = metrics.toString();
        TextMessage message = new TextMessage(json);
        
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    logger.error("Failed to send message to session {}", session.getId(), e);
                }
            }
        });
    }
    
    /**
     * 推送指标数据到指定应用的客户端
     */
    public void sendMetricsToApp(Long appId, ObjectNode metrics) {
        WebSocketSession session = appSessions.get(appId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(metrics.toString()));
            } catch (IOException e) {
                logger.error("Failed to send metrics to app {}", appId, e);
            }
        }
    }
    
    /**
     * 推送告警到所有客户端
     */
    public void broadcastAlert(ObjectNode alert) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "alert");
        message.set("data", alert);
        
        String json = message.toString();
        TextMessage textMessage = new TextMessage(json);
        
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    logger.error("Failed to send alert to session {}", session.getId(), e);
                }
            }
        });
    }
    
    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }
    
    /**
     * 获取指定应用的连接数
     */
    public int getAppConnectionCount(Long appId) {
        return appSessions.containsKey(appId) ? 1 : 0;
    }
}
