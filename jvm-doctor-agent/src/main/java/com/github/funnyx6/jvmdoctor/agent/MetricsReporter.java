package com.github.funnyx6.jvmdoctor.agent;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 指标上报模块
 * 负责定时采集并上报指标到 Server
 */
public class MetricsReporter {
    
    private final AgentConfig config;
    private final AppRegister appRegister;
    private final MetricsCollector collector;
    private final ScheduledExecutorService scheduler;
    
    private volatile boolean running = false;
    
    public MetricsReporter(AgentConfig config, AppRegister appRegister) {
        this.config = config;
        this.appRegister = appRegister;
        this.collector = new MetricsCollector();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jvm-doctor-metrics");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 启动指标上报
     */
    public void start() {
        if (running) {
            System.out.println("[MetricsReporter] Already started");
            return;
        }
        
        running = true;
        int interval = config.getReportInterval();
        
        System.out.println("[MetricsReporter] Starting with interval: " + interval + "s");
        
        // 立即执行一次采集上报
        reportOnce();
        
        // 定时上报
        scheduler.scheduleAtFixedRate(this::reportOnce, interval, interval, TimeUnit.SECONDS);
    }
    /**
     * 停止指标上报
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[MetricsReporter] Stopped");
    }
    
    /**
     * 执行一次指标采集和上报
     */
    private void reportOnce() {
        if (!running) return;
        
        try {
            Long appId = appRegister.getAppId();
            if (appId == null) {
                System.err.println("[MetricsReporter] App not registered, cannot report");
                return;
            }
            
            // 采集指标
            Map<String, Object> metrics = collector.collect();
            
            // 发送到 Server
            boolean success = sendMetrics(appId, metrics);
            
            if (success) {
                System.out.println("[MetricsReporter] Metrics reported successfully");
            } else {
                System.err.println("[MetricsReporter] Failed to report metrics");
            }
            
        } catch (Exception e) {
            System.err.println("[MetricsReporter] Error: " + e.getMessage());
        }
    }
    
    /**
     * 发送指标到 Server
     */
    private boolean sendMetrics(Long appId, Map<String, Object> metrics) {
        try {
            String url = config.getServerUrl() + "/api/metrics";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("appId", appId);
            requestBody.put("metrics", metrics);
            
            String json = toJson(requestBody);
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
            
        } catch (Exception e) {
            System.err.println("[MetricsReporter] Send error: " + e.getMessage());
            return false;
        }
    }
    
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
            } else if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }
    
    // ========== 静态工厂方法 ==========
    
    private static volatile MetricsReporter instance;
    
    public static synchronized void start(AgentConfig config) {
        if (instance != null) {
            System.out.println("[MetricsReporter] Already started");
            return;
        }
        
        // 先注册应用
        AppRegister appRegister = new AppRegister(config);
        Long appId = appRegister.register();
        if (appId == null) {
            System.err.println("[MetricsReporter] Failed to register app, metrics reporting disabled");
            return;
        }
        
        // 启动指标上报
        instance = new MetricsReporter(config, appRegister);
        instance.start();
        
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }));
    }
    
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }
    
    public static MetricsReporter getInstance() {
        return instance;
    }
}
