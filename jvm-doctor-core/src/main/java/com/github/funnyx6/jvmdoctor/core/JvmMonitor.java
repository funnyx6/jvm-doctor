package com.github.funnyx6.jvmdoctor.core;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JVM 监控器
 * 负责收集和监控 JVM 的各种指标
 */
public class JvmMonitor {
    private static final Logger logger = LoggerFactory.getLogger(JvmMonitor.class);
    
    private final ScheduledExecutorService scheduler;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final RuntimeMXBean runtimeMXBean;
    private final GarbageCollectorMXBean[] gcMXBeans;
    
    private Map<String, Object> lastMetrics;
    
    public JvmMonitor() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.meterRegistry = null; // 实际使用时需要初始化
        this.objectMapper = new ObjectMapper();
        this.lastMetrics = new HashMap<>();
        
        // 获取 MXBeans
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans().toArray(new GarbageCollectorMXBean[0]);
    }
    
    /**
     * 开始监控
     * @param intervalSeconds 监控间隔（秒）
     */
    public void startMonitoring(int intervalSeconds) {
        logger.info("Starting JVM monitoring with interval: {} seconds", intervalSeconds);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                collectMetrics();
                if (meterRegistry != null) {
                    publishMetrics();
                }
            } catch (Exception e) {
                logger.error("Error collecting metrics", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 停止监控
     */
    public void stopMonitoring() {
        logger.info("Stopping JVM monitoring");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 收集 JVM 指标
     */
    public void collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 内存指标
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        metrics.put("heap.used", heapUsage.getUsed());
        metrics.put("heap.committed", heapUsage.getCommitted());
        metrics.put("heap.max", heapUsage.getMax());
        metrics.put("heap.usage", (double) heapUsage.getUsed() / heapUsage.getCommitted());
        
        metrics.put("nonheap.used", nonHeapUsage.getUsed());
        metrics.put("nonheap.committed", nonHeapUsage.getCommitted());
        metrics.put("nonheap.max", nonHeapUsage.getMax());
        
        // GC 指标
        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            String gcName = gcBean.getName();
            metrics.put("gc." + gcName + ".count", gcBean.getCollectionCount());
            metrics.put("gc." + gcName + ".time", gcBean.getCollectionTime());
        }
        
        // 线程指标
        metrics.put("thread.count", threadMXBean.getThreadCount());
        metrics.put("thread.daemon", threadMXBean.getDaemonThreadCount());
        metrics.put("thread.peak", threadMXBean.getPeakThreadCount());
        metrics.put("thread.totalStarted", threadMXBean.getTotalStartedThreadCount());
        
        // 检测死锁
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        metrics.put("thread.deadlock", deadlockedThreads != null ? deadlockedThreads.length : 0);
        
        // 系统指标
        metrics.put("cpu.cores", osMXBean.getAvailableProcessors());
        metrics.put("system.load", getSystemLoad());
        
        // 运行时信息
        metrics.put("uptime", runtimeMXBean.getUptime());
        metrics.put("startTime", runtimeMXBean.getStartTime());
        metrics.put("jvm.name", runtimeMXBean.getVmName());
        metrics.put("jvm.version", runtimeMXBean.getVmVersion());
        
        lastMetrics = metrics;
        logger.debug("Collected {} metrics", metrics.size());
    }
    
    /**
     * 发布指标到 MeterRegistry
     */
    private void publishMetrics() {
        // 这里可以实现将指标发布到 Micrometer
        // 例如：meterRegistry.gauge("jvm.memory.heap.used", Tags.empty(), heapUsed);
    }
    
    /**
     * 获取系统负载
     */
    private double getSystemLoad() {
        try {
            // 尝试获取系统负载平均值
            if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsMXBean = 
                    (com.sun.management.OperatingSystemMXBean) osMXBean;
                return sunOsMXBean.getSystemLoadAverage();
            }
        } catch (Exception e) {
            logger.warn("Failed to get system load average", e);
        }
        return -1;
    }
    
    /**
     * 获取当前指标
     */
    public Map<String, Object> getCurrentMetrics() {
        return new HashMap<>(lastMetrics);
    }
    
    /**
     * 获取指标 JSON
     */
    public String getMetricsJson() {
        try {
            return objectMapper.writeValueAsString(lastMetrics);
        } catch (Exception e) {
            logger.error("Failed to serialize metrics to JSON", e);
            return "{}";
        }
    }
    
    /**
     * 检查是否有异常
     */
    public Map<String, Object> checkForIssues() {
        Map<String, Object> issues = new HashMap<>();
        
        // 检查内存使用率过高
        double heapUsage = (double) lastMetrics.get("heap.usage");
        if (heapUsage > 0.9) {
            issues.put("high_heap_usage", 
                String.format("Heap usage is high: %.2f%%", heapUsage * 100));
        }
        
        // 检查死锁
        int deadlockCount = (int) lastMetrics.get("thread.deadlock");
        if (deadlockCount > 0) {
            issues.put("deadlock_detected", 
                String.format("Detected %d deadlocked threads", deadlockCount));
        }
        
        // 检查 GC 频繁
        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            String gcName = gcBean.getName();
            long gcCount = (long) lastMetrics.get("gc." + gcName + ".count");
            long gcTime = (long) lastMetrics.get("gc." + gcName + ".time");
            
            // 简单的启发式规则：如果 GC 时间占总运行时间的比例过高
            long uptime = (long) lastMetrics.get("uptime");
            if (uptime > 0 && gcTime > uptime * 0.1) {
                issues.put("high_gc_time_" + gcName,
                    String.format("GC %s spent %.2f%% of time collecting", 
                        gcName, (double) gcTime / uptime * 100));
            }
        }
        
        return issues;
    }
    
    /**
     * 生成诊断报告
     */
    public ObjectNode generateDiagnosticReport() {
        ObjectNode report = objectMapper.createObjectNode();
        
        // 基本信息
        report.put("timestamp", System.currentTimeMillis());
        report.put("jvmName", runtimeMXBean.getVmName());
        report.put("jvmVersion", runtimeMXBean.getVmVersion());
        report.put("uptime", runtimeMXBean.getUptime());
        
        // 当前指标
        ObjectNode metricsNode = objectMapper.valueToTree(lastMetrics);
        report.set("metrics", metricsNode);
        
        // 检测到的问题
        Map<String, Object> issues = checkForIssues();
        if (!issues.isEmpty()) {
            ObjectNode issuesNode = objectMapper.valueToTree(issues);
            report.set("issues", issuesNode);
            
            // 添加建议
            ObjectNode suggestionsNode = objectMapper.createObjectNode();
            if (issues.containsKey("high_heap_usage")) {
                suggestionsNode.put("memory", 
                    "Consider increasing heap size (-Xmx) or optimizing memory usage");
            }
            if (issues.containsKey("deadlock_detected")) {
                suggestionsNode.put("deadlock", 
                    "Analyze thread dumps to identify and fix deadlocks");
            }
            boolean hasGcIssues = false;
            for (String key : issues.keySet()) {
                if (key.startsWith("high_gc_time")) {
                    hasGcIssues = true;
                    break;
                }
            }
            if (hasGcIssues) {
                suggestionsNode.put("gc", 
                    "Consider tuning GC parameters or optimizing object creation patterns");
            }
            report.set("suggestions", suggestionsNode);
        }
        
        return report;
    }
}