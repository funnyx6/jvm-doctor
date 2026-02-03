package com.github.funnyx6.jvmdoctor.web.dto;

import java.util.Map;

/**
 * 应用注册请求 DTO
 */
public class AppRegisterRequest {
    
    private String appName;
    private String host;
    private Integer port;
    private String jvmName;
    private String jvmVersion;
    private Long startTime;
    
    // Getters and Setters
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    
    public String getJvmName() { return jvmName; }
    public void setJvmName(String jvmName) { this.jvmName = jvmName; }
    
    public String getJvmVersion() { return jvmVersion; }
    public void setJvmVersion(String jvmVersion) { this.jvmVersion = jvmVersion; }
    
    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }
}
