package com.github.funnyx6.jvmdoctor.web.controller;

import com.github.funnyx6.jvmdoctor.web.entity.AppAlert;
import com.github.funnyx6.jvmdoctor.web.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    
    private final AlertService alertService;
    
    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }
    
    /**
     * 获取所有未处理告警
     * GET /api/alerts
     */
    @GetMapping
    public ResponseEntity<List<AppAlert>> getAllAlerts() {
        return ResponseEntity.ok(alertService.getAllAlerts());
    }
    
    /**
     * 获取未处理告警
     * GET /api/alerts/unacknowledged
     */
    @GetMapping("/unacknowledged")
    public ResponseEntity<List<AppAlert>> getUnacknowledgedAlerts() {
        return ResponseEntity.ok(alertService.getUnacknowledgedAlerts());
    }
    
    /**
     * 获取应用的告警
     * GET /api/alerts/app/{appId}
     */
    @GetMapping("/app/{appId}")
    public ResponseEntity<List<AppAlert>> getAlertsByApp(@PathVariable Long appId) {
        return ResponseEntity.ok(alertService.getAlertsByAppId(appId));
    }
    
    /**
     * 获取告警统计
     * GET /api/alerts/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAlertStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("unacknowledgedCount", alertService.getUnacknowledgedCount());
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 确认告警
     * POST /api/alerts/{alertId}/acknowledge
     */
    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeAlert(
            @PathVariable Long alertId,
            @RequestBody(required = false) Map<String, String> body) {
        String acknowledgedBy = body != null ? body.getOrDefault("acknowledgedBy", "unknown") : "unknown";
        alertService.acknowledgeAlert(alertId, acknowledgedBy);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Alert acknowledged");
        return ResponseEntity.ok(response);
    }
}
