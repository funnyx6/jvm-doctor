package com.github.funnyx6.jvmdoctor.web.dto;

import java.time.Instant;

/**
 * 应用注册响应 DTO
 */
public class AppRegisterResponse {
    
    private Long appId;
    private String status;
    private String message;
    private Long serverTime;
    
    public AppRegisterResponse(Long appId, String status, String message) {
        this.appId = appId;
        this.status = status;
        this.message = message;
        this.serverTime = Instant.now().toEpochMilli();
    }
    
    // Getters and Setters
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public Long getServerTime() { return serverTime; }
    public void setServerTime(Long serverTime) { this.serverTime = serverTime; }
}
