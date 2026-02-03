package com.github.funnyx6.jvmdoctor.agent;

import java.lang.management.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 指标采集器
 * 从 JVM MXBean 收集各项指标
 */
public class MetricsCollector {
    
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final RuntimeMXBean runtimeMXBean;
    private final GarbageCollectorMXBean[] gcMXBeans;
    
    public MetricsCollector() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans().toArray(new GarbageCollectorMXBean[0]);
    }
    
    /**
     * 采集当前所有指标
     * 
     * @return 指标 Map
     */
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 内存指标
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        metrics.put("heap.used", heapUsage.getUsed());
        metrics.put("heap.max", heapUsage.getMax());
        metrics.put("heap.committed", heapUsage.getCommitted());
        if (heapUsage.getMax() > 0) {
            metrics.put("heap.usage", (double) heapUsage.getUsed() / heapUsage.getMax());
        } else {
            metrics.put("heap.usage", 0.0);
        }
        
        metrics.put("nonheap.used", nonHeapUsage.getUsed());
        metrics.put("nonheap.committed", nonHeapUsage.getCommitted());
        
        // GC 指标
        long totalGcCount = 0;
        long totalGcTime = 0;
        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            totalGcCount += gcBean.getCollectionCount();
            totalGcTime += gcBean.getCollectionTime();
        }
        metrics.put("gc.count", totalGcCount);
        metrics.put("gc.time", totalGcTime);
        
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
        metrics.put("cpu.load", getCpuLoad());
        metrics.put("system.load", getSystemLoad());
        
        // 运行时信息
        metrics.put("uptime", runtimeMXBean.getUptime());
        
        return metrics;
    }
    
    /**
     * 获取 CPU 使用率
     */
    private Double getCpuLoad() {
        try {
            if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOsMXBean = 
                    (com.sun.management.OperatingSystemMXBean) osMXBean;
                return sunOsMXBean.getCpuLoad();
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }
    
    /**
     * 获取系统负载
     */
    private Double getSystemLoad() {
        try {
            double load = osMXBean.getSystemLoadAverage();
            if (load >= 0) {
                return load;
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }
}
