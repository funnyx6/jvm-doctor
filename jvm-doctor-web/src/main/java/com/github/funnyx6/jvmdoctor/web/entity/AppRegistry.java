package com.github.funnyx6.jvmdoctor.web.entity;

import javax.persistence.*;
import java.time.Instant;

/**
 * 应用注册表实体
 */
@Entity
@Table(name = "app_registry")
public class AppRegistry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "app_name", nullable = false, length = 128)
    private String appName;
    
    @Column(nullable = false, length = 64)
    private String host;
    
    @Column(nullable = false)
    private Integer port;
    
    @Column(name = "jvm_name", length = 128)
    private String jvmName;
    
    @Column(name = "jvm_version", length = 64)
    private String jvmVersion;
    
    @Column(name = "start_time")
    private Long startTime;
    
    @Column(length = 16)
    private String status = "running";
    
    @Column(name = "registered_at")
    private Long registeredAt;
    
    @Column(name = "last_heartbeat")
    private Long lastHeartbeat;
    
    @Column(name = "thread_server_port")
    private Integer threadServerPort;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
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
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Long getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Long registeredAt) { this.registeredAt = registeredAt; }
    
    public Long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    
    public Integer getThreadServerPort() { return threadServerPort; }
    public void setThreadServerPort(Integer threadServerPort) { this.threadServerPort = threadServerPort; }
}
