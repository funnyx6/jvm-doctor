package com.github.funnyx6.jvmdoctor.web.service;

import com.github.funnyx6.jvmdoctor.web.entity.AppAlert;
import com.github.funnyx6.jvmdoctor.web.repository.AppAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AlertService {
    
    private final AppAlertRepository alertRepository;
    
    public AlertService(AppAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }
    
    /**
     * 获取所有未处理告警
     */
    public List<AppAlert> getUnacknowledgedAlerts() {
        return alertRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }
    
    /**
     * 获取应用的告警历史
     */
    public List<AppAlert> getAlertsByAppId(Long appId) {
        return alertRepository.findByAppIdOrderByCreatedAtDesc(appId);
    }
    
    /**
     * 获取所有告警
     */
    public List<AppAlert> getAllAlerts() {
        return alertRepository.findAllByOrderByCreatedAtDesc();
    }
    
    /**
     * 获取告警统计
     */
    public long getUnacknowledgedCount() {
        return alertRepository.countByAcknowledgedFalse();
    }
    
    /**
     * 确认告警
     */
    @Transactional
    public void acknowledgeAlert(Long alertId, String acknowledgedBy) {
        alertRepository.findById(alertId).ifPresent(alert -> {
            alert.setAcknowledged(true);
            alert.setAcknowledgedAt(Instant.now().toEpochMilli());
            alert.setAcknowledgedBy(acknowledgedBy);
            alertRepository.save(alert);
        });
    }
}
