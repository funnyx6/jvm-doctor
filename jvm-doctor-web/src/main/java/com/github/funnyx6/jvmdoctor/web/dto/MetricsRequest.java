package com.github.funnyx6.jvmdoctor.web.dto;

import java.util.Map;

/**
 * 指标上报请求 DTO（与 Agent 协议对应）
 */
public class MetricsRequest {
    
    private Long appId;
    private Map<String, Object> metrics;
    
    // Getters and Setters
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
}
