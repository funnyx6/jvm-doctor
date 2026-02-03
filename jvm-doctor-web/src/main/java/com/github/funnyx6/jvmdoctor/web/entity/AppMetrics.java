package com.github.funnyx6.jvmdoctor.web.entity;

import javax.persistence.*;
import java.time.Instant;

/**
 * 指标数据表实体
 */
@Entity
@Table(name = "app_metrics", indexes = {
    @Index(name = "idx_app_timestamp", columnList = "app_id, timestamp")
})
public class AppMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "app_id", nullable = false)
    private Long appId;
    
    @Column(nullable = false)
    private Long timestamp;
    
    @Column(name = "heap_used")
    private Long heapUsed;
    
    @Column(name = "heap_max")
    private Long heapMax;
    
    @Column(name = "heap_usage")
    private Double heapUsage;
    
    @Column(name = "nonheap_used")
    private Long nonheapUsed;
    
    @Column(name = "gc_count")
    private Long gcCount;
    
    @Column(name = "gc_time")
    private Long gcTime;
    
    @Column(name = "thread_count")
    private Integer threadCount;
    
    @Column(name = "daemon_thread_count")
    private Integer daemonThreadCount;
    
    @Column(name = "cpu_usage")
    private Double cpuUsage;
    
    @Column(name = "system_load")
    private Double systemLoad;
    
    @Column(name = "uptime")
    private Long uptime;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    
    public Long getHeapUsed() { return heapUsed; }
    public void setHeapUsed(Long heapUsed) { this.heapUsed = heapUsed; }
    
    public Long getHeapMax() { return heapMax; }
    public void setHeapMax(Long heapMax) { this.heapMax = heapMax; }
    
    public Double getHeapUsage() { return heapUsage; }
    public void setHeapUsage(Double heapUsage) { this.heapUsage = heapUsage; }
    
    public Long getNonheapUsed() { return nonheapUsed; }
    public void setNonheapUsed(Long nonheapUsed) { this.nonheapUsed = nonheapUsed; }
    
    public Long getGcCount() { return gcCount; }
    public void setGcCount(Long gcCount) { this.gcCount = gcCount; }
    
    public Long getGcTime() { return gcTime; }
    public void setGcTime(Long gcTime) { this.gcTime = gcTime; }
    
    public Integer getThreadCount() { return threadCount; }
    public void setThreadCount(Integer threadCount) { this.threadCount = threadCount; }
    
    public Integer getDaemonThreadCount() { return daemonThreadCount; }
    public void setDaemonThreadCount(Integer daemonThreadCount) { this.daemonThreadCount = daemonThreadCount; }
    
    public Double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(Double cpuUsage) { this.cpuUsage = cpuUsage; }
    
    public Double getSystemLoad() { return systemLoad; }
    public void setSystemLoad(Double systemLoad) { this.systemLoad = systemLoad; }
    
    public Long getUptime() { return uptime; }
    public void setUptime(Long uptime) { this.uptime = uptime; }
}
