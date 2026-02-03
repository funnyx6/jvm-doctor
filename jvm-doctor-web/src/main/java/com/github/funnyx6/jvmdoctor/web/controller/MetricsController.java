package com.github.funnyx6.jvmdoctor.web.controller;

import com.github.funnyx6.jvmdoctor.web.dto.MetricsRequest;
import com.github.funnyx6.jvmdoctor.web.entity.AppMetrics;
import com.github.funnyx6.jvmdoctor.web.entity.AppRegistry;
import com.github.funnyx6.jvmdoctor.web.service.AppRegistryService;
import com.github.funnyx6.jvmdoctor.web.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    
    private final MetricsService metricsService;
    private final AppRegistryService appRegistryService;
    
    public MetricsController(MetricsService metricsService, AppRegistryService appRegistryService) {
        this.metricsService = metricsService;
        this.appRegistryService = appRegistryService;
    }
    
    /**
     * 接收指标数据
     * POST /api/metrics
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveMetrics(@RequestBody MetricsRequest request) {
        AppMetrics metrics = convertToEntity(request.getMetrics());
        metricsService.saveMetrics(request.getAppId(), metrics);
        
        // 更新心跳
        appRegistryService.heartbeat(request.getAppId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Metrics received");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取应用的最新指标
     * GET /api/metrics/{appId}/latest
     */
    @GetMapping("/{appId}/latest")
    public ResponseEntity<AppMetrics> getLatestMetrics(@PathVariable Long appId) {
        AppMetrics metrics = metricsService.getLatestMetrics(appId);
        if (metrics != null) {
            return ResponseEntity.ok(metrics);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 获取应用的指标历史
     * GET /api/metrics/{appId}/history?since=timestamp
     */
    @GetMapping("/{appId}/history")
    public ResponseEntity<List<AppMetrics>> getMetricsHistory(
            @PathVariable Long appId,
            @RequestParam(required = false, defaultValue = "0") Long since) {
        return ResponseEntity.ok(metricsService.getMetricsHistory(appId, since));
    }
    
    /**
     * 获取所有应用的最新指标
     * GET /api/metrics/all/latest
     */
    @GetMapping("/all/latest")
    public ResponseEntity<List<AppMetrics>> getAllLatestMetrics() {
        return ResponseEntity.ok(metricsService.getAllLatestMetrics());
    }
    
    /**
     * 批量接收指标（支持上报多应用）
     * POST /api/metrics/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchMetrics(@RequestBody List<MetricsRequest> requests) {
        int count = 0;
        for (MetricsRequest request : requests) {
            AppMetrics metrics = convertToEntity(request.getMetrics());
            metricsService.saveMetrics(request.getAppId(), metrics);
            appRegistryService.heartbeat(request.getAppId());
            count++;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("processed", count);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 将 Map 转换为 AppMetrics 实体
     */
    private AppMetrics convertToEntity(java.util.Map<String, Object> map) {
        AppMetrics metrics = new AppMetrics();
        
        if (map.containsKey("heap.used")) {
            metrics.setHeapUsed(getLong(map, "heap.used"));
        }
        if (map.containsKey("heap.max")) {
            metrics.setHeapMax(getLong(map, "heap.max"));
        }
        if (map.containsKey("nonheap.used")) {
            metrics.setNonheapUsed(getLong(map, "nonheap.used"));
        }
        if (map.containsKey("gc.count")) {
            metrics.setGcCount(getLong(map, "gc.count"));
        }
        if (map.containsKey("gc.time")) {
            metrics.setGcTime(getLong(map, "gc.time"));
        }
        if (map.containsKey("thread.count")) {
            metrics.setThreadCount(getInt(map, "thread.count"));
        }
        if (map.containsKey("thread.daemon")) {
            metrics.setDaemonThreadCount(getInt(map, "thread.daemon"));
        }
        if (map.containsKey("cpu.cores")) {
            // CPU 核数不存，只存使用率
        }
        if (map.containsKey("system.load")) {
            metrics.setSystemLoad(getDouble(map, "system.load"));
        }
        if (map.containsKey("uptime")) {
            metrics.setUptime(getLong(map, "uptime"));
        }
        
        return metrics;
    }
    
    private Long getLong(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
    
    private Integer getInt(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
    
    private Double getDouble(java.util.Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
}
