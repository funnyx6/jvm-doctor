package com.github.funnyx6.jvmdoctor.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.funnyx6.jvmdoctor.web.entity.AppAlert;
import com.github.funnyx6.jvmdoctor.web.entity.AppMetrics;
import com.github.funnyx6.jvmdoctor.web.entity.AppRegistry;
import com.github.funnyx6.jvmdoctor.web.repository.AppAlertRepository;
import com.github.funnyx6.jvmdoctor.web.repository.AppMetricsRepository;
import com.github.funnyx6.jvmdoctor.web.websocket.MetricsWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    private final AppMetricsRepository metricsRepository;
    private final AppAlertRepository alertRepository;
    private final AppRegistryService appRegistryService;
    private final MetricsWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    
    // 告警阈值
    private static final double HEAP_USAGE_THRESHOLD = 0.9;
    private static final double CPU_USAGE_THRESHOLD = 0.8;
    private static final long HEARTBEAT_TIMEOUT_MS = 120_000; // 2分钟
    
    public MetricsService(
            AppMetricsRepository metricsRepository,
            AppAlertRepository alertRepository,
            AppRegistryService appRegistryService,
            MetricsWebSocketHandler webSocketHandler,
            ObjectMapper objectMapper) {
        this.metricsRepository = metricsRepository;
        this.alertRepository = alertRepository;
        this.appRegistryService = appRegistryService;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 接收并保存指标数据
     */
    @Transactional
    public AppMetrics saveMetrics(Long appId, AppMetrics metrics) {
        metrics.setAppId(appId);
        metrics.setTimestamp(Instant.now().toEpochMilli());
        
        // 计算堆使用率
        if (metrics.getHeapUsed() != null && metrics.getHeapMax() != null && metrics.getHeapMax() > 0) {
            metrics.setHeapUsage((double) metrics.getHeapUsed() / metrics.getHeapMax());
        }
        
        AppMetrics saved = metricsRepository.save(metrics);
        
        // 通过 WebSocket 推送指标
        pushMetricsToWebSocket(appId, saved);
        
        // 检查告警
        checkAndCreateAlerts(appId, metrics);
        
        logger.debug("Saved metrics for appId: {}", appId);
        return saved;
    }
    
    /**
     * 通过 WebSocket 推送指标
     */
    private void pushMetricsToWebSocket(Long appId, AppMetrics metrics) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", "metrics");
            data.put("appId", appId);
            data.put("timestamp", metrics.getTimestamp());
            data.put("heapUsed", metrics.getHeapUsed() != null ? metrics.getHeapUsed() : 0);
            data.put("heapMax", metrics.getHeapMax() != null ? metrics.getHeapMax() : 0);
            data.put("heapUsage", metrics.getHeapUsage() != null ? metrics.getHeapUsage() : 0);
            data.put("gcCount", metrics.getGcCount() != null ? metrics.getGcCount() : 0);
            data.put("gcTime", metrics.getGcTime() != null ? metrics.getGcTime() : 0);
            data.put("threadCount", metrics.getThreadCount() != null ? metrics.getThreadCount() : 0);
            data.put("cpuUsage", metrics.getCpuUsage() != null ? metrics.getCpuUsage() : 0);
            data.put("systemLoad", metrics.getSystemLoad() != null ? metrics.getSystemLoad() : 0);
            data.put("uptime", metrics.getUptime() != null ? metrics.getUptime() : 0);
            
            webSocketHandler.broadcastMetrics(data);
        } catch (Exception e) {
            logger.error("Failed to push metrics via WebSocket", e);
        }
    }
    
    /**
     * 获取应用的最新指标
     */
    public AppMetrics getLatestMetrics(Long appId) {
        return metricsRepository.findLatestByAppId(appId);
    }
    
    /**
     * 获取应用的指标历史
     */
    public List<AppMetrics> getMetricsHistory(Long appId, long sinceTimestamp) {
        if (sinceTimestamp > 0) {
            return metricsRepository.findByAppIdAndTimestampAfter(appId, sinceTimestamp);
        }
        return metricsRepository.findByAppIdOrderByTimestampDesc(appId);
    }
    
    /**
     * 获取所有应用的最新指标
     */
    public List<AppMetrics> getAllLatestMetrics() {
        List<AppRegistry> apps = appRegistryService.getRunningApps();
        return apps.stream()
                .map(app -> metricsRepository.findLatestByAppId(app.getId()))
                .filter(m -> m != null)
                .toList();
    }
    
    /**
     * 检查并创建告警
     */
    private void checkAndCreateAlerts(Long appId, AppMetrics metrics) {
        // 堆内存告警
        if (metrics.getHeapUsage() != null && metrics.getHeapUsage() > HEAP_USAGE_THRESHOLD) {
            createAlert(appId, "high_heap_usage", 
                    String.format("Heap usage: %.1f%%", metrics.getHeapUsage() * 100), 
                    "warning");
        }
        
        // CPU 告警
        if (metrics.getCpuUsage() != null && metrics.getCpuUsage() > CPU_USAGE_THRESHOLD) {
            createAlert(appId, "high_cpu_usage",
                    String.format("CPU usage: %.1f%%", metrics.getCpuUsage() * 100),
                    "warning");
        }
        
        // GC 频繁告警（简单判断：GC 时间占比超过 10%）
        if (metrics.getUptime() != null && metrics.getUptime() > 0 && 
                metrics.getGcTime() != null && metrics.getGcTime() > metrics.getUptime() * 0.1) {
            createAlert(appId, "high_gc_time",
                    String.format("GC time ratio: %.1f%%", (double) metrics.getGcTime() / metrics.getUptime() * 100),
                    "warning");
        }
    }
    
    /**
     * 创建告警
     */
    private void createAlert(Long appId, String type, String msg, String level) {
        // 检查最近是否有相同类型的未处理告警（避免重复）
        List<AppAlert> recentAlerts = alertRepository.findByAppIdOrderByCreatedAtDesc(appId);
        boolean hasRecent = recentAlerts.stream()
                .filter(a -> !a.getAcknowledged())
                .anyMatch(a -> a.getAlertType().equals(type) && 
                        System.currentTimeMillis() - a.getCreatedAt() < 300_000); // 5分钟内
        
        if (!hasRecent) {
            AppAlert alert = new AppAlert();
            alert.setAppId(appId);
            alert.setAlertType(type);
            alert.setAlertMsg(msg);
            alert.setAlertLevel(level);
            alert.setCreatedAt(Instant.now().toEpochMilli());
            alert.setAcknowledged(false);
            alertRepository.save(alert);
            
            // 通过 WebSocket 推送告警
            pushAlertToWebSocket(appId, alert);
            
            logger.warn("Alert created for appId {}: {} - {}", appId, type, msg);
        }
    }
    
    /**
     * 通过 WebSocket 推送告警
     */
    private void pushAlertToWebSocket(Long appId, AppAlert alert) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", "alert");
            data.put("alertId", alert.getId());
            data.put("appId", appId);
            data.put("alertType", alert.getAlertType());
            data.put("alertMsg", alert.getAlertMsg());
            data.put("alertLevel", alert.getAlertLevel());
            data.put("createdAt", alert.getCreatedAt());
            
            webSocketHandler.broadcastAlert(data);
        } catch (Exception e) {
            logger.error("Failed to push alert via WebSocket", e);
        }
    }
    
    /**
     * 定时检查心跳超时（每分钟执行）
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkHeartbeatTimeout() {
        appRegistryService.markOfflineApps(HEARTBEAT_TIMEOUT_MS);
    }
    
    /**
     * 清理旧数据（保留7天）
     */
    @Scheduled(fixedRate = 3600000) // 每小时
    @Transactional
    public void cleanupOldData() {
        long cutoff = Instant.now().toEpochMilli() - 7 * 24 * 60 * 60 * 1000L;
        metricsRepository.deleteByTimestampBefore(cutoff);
        logger.info("Cleaned up metrics older than 7 days");
    }
}
