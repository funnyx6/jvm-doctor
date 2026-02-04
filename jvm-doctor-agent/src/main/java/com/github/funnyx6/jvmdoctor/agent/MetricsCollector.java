package com.github.funnyx6.jvmdoctor.agent;

import java.lang.management.*;
import java.util.*;

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
     * @param includeCustom 是否包含自定义指标
     * @return 指标 Map
     */
    public Map<String, Object> collect(boolean includeCustom) {
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
        
        // 自定义指标
        if (includeCustom) {
            Map<String, Object> customMetrics = GlobalCollectors.collectAll();
            metrics.putAll(customMetrics);
        }
        
        return metrics;
    }
    
    /**
     * 采集当前所有指标（包含自定义指标）
     */
    public Map<String, Object> collect() {
        return collect(true);
    }
    
    /**
     * 获取所有线程信息
     * 
     * @return 线程信息列表
     */
    public List<Map<String, Object>> collectThreadInfo() {
        List<Map<String, Object>> threads = new ArrayList<>();
        
        // 获取所有线程 ID
        long[] threadIds = threadMXBean.getAllThreadIds();
        
        // 获取线程 CPU 时间
        Map<Long, Long> threadCpuTimes = getThreadCpuTimes();
        
        for (long threadId : threadIds) {
            try {
                // JDK 8: dumpAllThreads 返回 ThreadInfo[]
                ThreadInfo threadInfo = threadMXBean.getThreadInfo(threadId, 50);
                if (threadInfo == null || threadInfo.getThreadName() == null) {
                    continue;
                }
                
                Map<String, Object> thread = new HashMap<>();
                thread.put("threadId", threadId);
                thread.put("name", threadInfo.getThreadName());
                thread.put("state", threadInfo.getThreadState().name());
                thread.put("priority", threadInfo.getPriority());
                thread.put("cpuTime", threadCpuTimes.getOrDefault(threadId, 0L));
                thread.put("cpuTimeMillis", threadCpuTimes.getOrDefault(threadId, 0L) / 1_000_000);
                thread.put("userTime", threadMXBean.getThreadUserTime(threadId));
                
                // 是否为守护线程
                thread.put("daemon", threadInfo.isDaemon());
                
                // 阻塞计数
                thread.put("blockedCount", threadInfo.getBlockedCount());
                thread.put("waitedCount", threadInfo.getWaitedCount());
                
                // 锁信息
                String lockName = threadInfo.getLockName();
                if (lockName != null) {
                    thread.put("lockName", lockName);
                    thread.put("lockOwnerId", threadInfo.getLockOwnerId());
                    thread.put("lockOwnerName", threadInfo.getLockOwnerName());
                }
                
                threads.add(thread);
            } catch (Exception e) {
                // 忽略单个线程采集异常
            }
        }
        
        return threads;
    }
    
    /**
     * 获取 CPU 占用 Top 线程
     * 
     * @param topN Top N
     * @return 线程信息列表
     */
    public List<Map<String, Object>> getTopCpuThreads(int topN) {
        List<Map<String, Object>> threads = collectThreadInfo();
        
        // 按 CPU 时间排序
        threads.sort((a, b) -> {
            Long cpuA = ((Number) a.get("cpuTime")).longValue();
            Long cpuB = ((Number) b.get("cpuTime")).longValue();
            return cpuB.compareTo(cpuA);
        });
        
        // 返回 Top N
        return threads.size() > topN ? threads.subList(0, topN) : threads;
    }
    
    /**
     * 获取指定线程的堆栈信息
     * 
     * @param threadId 线程 ID
     * @return 线程堆栈信息
     */
    public Map<String, Object> getThreadStack(long threadId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            ThreadInfo threadInfo = threadMXBean.getThreadInfo(threadId, 200);
            if (threadInfo == null) {
                result.put("error", "Thread not found");
                return result;
            }
            
            result.put("threadId", threadId);
            result.put("name", threadInfo.getThreadName());
            result.put("state", threadInfo.getThreadState().name());
            result.put("priority", threadInfo.getPriority());
            result.put("daemon", threadInfo.isDaemon());
            
            // CPU 时间
            Map<Long, Long> threadCpuTimes = getThreadCpuTimes();
            result.put("cpuTimeMillis", threadCpuTimes.getOrDefault(threadId, 0L) / 1_000_000);
            
            // 锁信息
            String lockName = threadInfo.getLockName();
            if (lockName != null) {
                result.put("lockName", lockName);
                result.put("lockOwnerId", threadInfo.getLockOwnerId());
                result.put("lockOwnerName", threadInfo.getLockOwnerName());
            }
            
            // 堆栈信息
            StackTraceElement[] stackTrace = threadInfo.getStackTrace();
            List<Map<String, String>> stack = new ArrayList<>();
            for (StackTraceElement element : stackTrace) {
                Map<String, String> frame = new HashMap<>();
                frame.put("className", element.getClassName());
                frame.put("methodName", element.getMethodName());
                frame.put("fileName", element.getFileName());
                frame.put("lineNumber", String.valueOf(element.getLineNumber()));
                frame.put("nativeMethod", String.valueOf(element.isNativeMethod()));
                stack.add(frame);
            }
            result.put("stackTrace", stack);
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取死锁线程列表
     * 
     * @return 死锁线程信息
     */
    public List<Map<String, Object>> getDeadlockedThreads() {
        List<Map<String, Object>> deadlocks = new ArrayList<>();
        
        long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
        if (deadlockedThreadIds == null || deadlockedThreadIds.length == 0) {
            return deadlocks;
        }
        
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);
        
        for (ThreadInfo threadInfo : threadInfos) {
            if (threadInfo == null) {
                continue;
            }
            
            Map<String, Object> thread = new HashMap<>();
            thread.put("threadId", threadInfo.getThreadId());
            thread.put("name", threadInfo.getThreadName());
            thread.put("state", threadInfo.getThreadState().name());
            thread.put("lockName", threadInfo.getLockName());
            thread.put("lockOwnerName", threadInfo.getLockOwnerName());
            
            // 堆栈
            StackTraceElement[] stackTrace = threadInfo.getStackTrace();
            List<Map<String, String>> stack = new ArrayList<>();
            for (StackTraceElement element : stackTrace) {
                Map<String, String> frame = new HashMap<>();
                frame.put("className", element.getClassName());
                frame.put("methodName", element.getMethodName());
                frame.put("fileName", element.getFileName());
                frame.put("lineNumber", String.valueOf(element.getLineNumber()));
                stack.add(frame);
            }
            thread.put("stackTrace", stack);
            
            deadlocks.add(thread);
        }
        
        return deadlocks;
    }
    
    /**
     * 获取所有线程的 CPU 时间
     */
    private Map<Long, Long> getThreadCpuTimes() {
        Map<Long, Long> cpuTimes = new HashMap<>();
        try {
            long[] threadIds = threadMXBean.getAllThreadIds();
            for (long threadId : threadIds) {
                long cpuTime = threadMXBean.getThreadCpuTime(threadId);
                if (cpuTime >= 0) {
                    cpuTimes.put(threadId, cpuTime);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return cpuTimes;
    }
    
    /**
     * 获取 CPU 使用率
     */
    private Double getCpuLoad() {
        try {
            Class<?> sunOsClass = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (sunOsClass.isInstance(osMXBean)) {
                java.lang.reflect.Method method = sunOsClass.getMethod("getCpuLoad");
                Object result = method.invoke(osMXBean);
                if (result instanceof Double) {
                    return (Double) result;
                }
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
