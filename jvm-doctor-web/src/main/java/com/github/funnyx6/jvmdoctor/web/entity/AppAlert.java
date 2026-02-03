package com.github.funnyx6.jvmdoctor.web.entity;

import javax.persistence.*;
import java.time.Instant;

/**
 * 告警记录表实体
 */
@Entity
@Table(name = "app_alerts")
public class AppAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "app_id", nullable = false)
    private Long appId;
    
    @Column(name = "alert_type", nullable = false, length = 32)
    private String alertType;
    
    @Column(name = "alert_msg", columnDefinition = "TEXT")
    private String alertMsg;
    
    @Column(name = "alert_level", nullable = false, length = 16)
    private String alertLevel = "warning"; // info, warning, critical
    
    @Column(name = "created_at", nullable = false)
    private Long createdAt;
    
    @Column(name = "acknowledged")
    private Boolean acknowledged = false;
    
    @Column(name = "acknowledged_at")
    private Long acknowledgedAt;
    
    @Column(name = "acknowledged_by", length = 64)
    private String acknowledgedBy;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    
    public String getAlertMsg() { return alertMsg; }
    public void setAlertMsg(String alertMsg) { this.alertMsg = alertMsg; }
    
    public String getAlertLevel() { return alertLevel; }
    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
    
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    
    public Boolean getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Boolean acknowledged) { this.acknowledged = acknowledged; }
    
    public Long getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Long acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
}
