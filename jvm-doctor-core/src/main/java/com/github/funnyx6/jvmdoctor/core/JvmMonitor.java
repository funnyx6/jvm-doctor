package com.github.funnyx6.jvmdoctor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.lang.management.*;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    private final ObjectMapper objectMapper;
    
    private MBeanServerConnection mbeanConnection;
    private final boolean isRemote;
    private final long targetPid;
    
    private Map<String, Object> lastMetrics;
    
    /**
     * 监控当前 JVM
     */
    public JvmMonitor() {
        this(-1);
    }
    
    /**
     * 监控指定 PID 的 JVM
     * @param pid 目标进程 ID（小于等于0时监控当前进程）
     */
    public JvmMonitor(long pid) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.objectMapper = new ObjectMapper();
        this.lastMetrics = new HashMap<>();
        this.targetPid = pid > 0 ? pid : -1;
        this.isRemote = pid > 0;
        
        if (isRemote) {
            initRemoteConnection(pid);
        }
    }
    
    /**
     * 初始化远程 JMX 连接
     */
    private void initRemoteConnection(long pid) {
        try {
            // 动态 attach 到目标 JVM 获取 JMX connector 地址
            String connectorAddress = attachAndGetConnectorAddress(pid);
            
            if (connectorAddress != null) {
                JMXServiceURL url = new JMXServiceURL(connectorAddress);
                JMXConnector connector = JMXConnectorFactory.connect(url);
                mbeanConnection = connector.getMBeanServerConnection();
                logger.info("Connected to remote JVM PID {}", pid);
            } else {
                logger.warn("Could not get JMX connector for PID {}, using local metrics", pid);
            }
        } catch (Exception e) {
            logger.warn("Failed to connect to remote JVM PID {}: {}", pid, e.getMessage());
            mbeanConnection = null;
        }
    }
    
    /**
     * Attach 到目标 JVM 并获取 JMX connector 地址
     */
    private String attachAndGetConnectorAddress(long pid) {
        try {
            // 加载 tools.jar
            String javaHome = System.getProperty("java.home");
            File toolsJar = new File(javaHome + File.separator + ".." + File.separator + "lib" + File.separator + "tools.jar");
            
            if (!toolsJar.exists()) {
                logger.warn("tools.jar not found at {}", toolsJar.getPath());
                return null;
            }
            
            // 使用 URLClassLoader 动态加载
            java.net.URL[] urls = { toolsJar.toURI().toURL() };
            ClassLoader loader = new java.net.URLClassLoader(urls, JvmMonitor.class.getClassLoader());
            
            // 加载 VirtualMachine 类
            Class<?> vmClass = loader.loadClass("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, String.valueOf(pid));
            
            // 获取 JMX connector 地址
            java.lang.reflect.Method getAgentProperties = vmClass.getMethod("getAgentProperties");
            @SuppressWarnings("unchecked")
            java.util.Properties props = (java.util.Properties) getAgentProperties.invoke(vm);
            String address = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
            
            if (address == null) {
                // 尝试启动本地管理代理
                try {
                    java.lang.reflect.Method startAgent = vmClass.getMethod("startLocalManagementAgent", String.class);
                    address = (String) startAgent.invoke(vm, null);
                } catch (NoSuchMethodException e) {
                    // JDK 9+ 方式
                    java.lang.reflect.Method getDiagnosticOptions = vmClass.getMethod("getDiagnosticOptions");
                    Object options = getDiagnosticOptions.invoke(vm);
                    // 使用 agent 属性
                    java.lang.reflect.Method getAgentProperties2 = vmClass.getMethod("getAgentProperties");
                    @SuppressWarnings("unchecked")
                    java.util.Properties props2 = (java.util.Properties) getAgentProperties2.invoke(vm);
                    address = props2.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                }
            }
            
            vmClass.getMethod("detach").invoke(vm);
            
            return address;
        } catch (Exception e) {
            logger.debug("Failed to attach to PID {}: {}", pid, e.getMessage());
            return null;
        }
    }
    
    /**
     * 通过 JMX 获取远程 MXBean 属性值
     */
    private Object getRemoteAttribute(String mbeanName, String attribute) {
        if (mbeanConnection == null) return null;
        try {
            ObjectName name = new ObjectName(mbeanName);
            return mbeanConnection.getAttribute(name, attribute);
        } catch (Exception e) {
            return null;
        }
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
        
        try {
            if (isRemote && mbeanConnection != null) {
                // 远程 JVM 指标
                collectRemoteMetrics(metrics);
            } else {
                // 当前 JVM 指标
                collectLocalMetrics(metrics);
            }
        } catch (Exception e) {
            logger.error("Error collecting metrics", e);
        }
        
        lastMetrics = metrics;
        logger.debug("Collected {} metrics", metrics.size());
    }
    
    /**
     * 收集当前 JVM 指标
     */
    private void collectLocalMetrics(Map<String, Object> metrics) {
        // 内存指标
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        
        metrics.put("heap.used", heapUsage.getUsed());
        metrics.put("heap.committed", heapUsage.getCommitted());
        metrics.put("heap.max", heapUsage.getMax());
        metrics.put("heap.usage", (double) heapUsage.getUsed() / heapUsage.getMax());
        
        metrics.put("nonheap.used", nonHeapUsage.getUsed());
        metrics.put("nonheap.committed", nonHeapUsage.getCommitted());
        
        // GC 指标
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String gcName = gcBean.getName().replaceAll("\\s+", "_");
            metrics.put("gc." + gcName + ".count", gcBean.getCollectionCount());
            metrics.put("gc." + gcName + ".time", gcBean.getCollectionTime());
        }
        
        // 线程指标
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        metrics.put("thread.count", threadBean.getThreadCount());
        metrics.put("thread.daemon", threadBean.getDaemonThreadCount());
        metrics.put("thread.peak", threadBean.getPeakThreadCount());
        
        // 死锁检测
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        metrics.put("thread.deadlock", deadlockedThreads != null ? deadlockedThreads.length : 0);
        
        // 系统指标
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        metrics.put("cpu.cores", osBean.getAvailableProcessors());
        metrics.put("system.load", osBean.getSystemLoadAverage());
        
        // 运行时信息
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        metrics.put("uptime", runtimeBean.getUptime());
        metrics.put("jvm.name", runtimeBean.getVmName());
        metrics.put("jvm.version", runtimeBean.getVmVersion());
    }
    
    /**
     * 收集远程 JVM 指标
     */
    private void collectRemoteMetrics(Map<String, Object> metrics) {
        // 内存指标
        MemoryUsage heapUsage = (MemoryUsage) getRemoteAttribute("java.lang:type=Memory", "HeapMemoryUsage");
        if (heapUsage != null) {
            metrics.put("heap.used", heapUsage.getUsed());
            metrics.put("heap.max", heapUsage.getMax());
            metrics.put("heap.usage", (double) heapUsage.getUsed() / heapUsage.getMax());
        }
        
        // 线程指标
        metrics.put("thread.count", getRemoteAttribute("java.lang:type=Threading", "ThreadCount"));
        metrics.put("thread.daemon", getRemoteAttribute("java.lang:type=Threading", "DaemonThreadCount"));
        metrics.put("thread.peak", getRemoteAttribute("java.lang:type=Threading", "PeakThreadCount"));
        
        // 系统指标
        metrics.put("cpu.cores", getRemoteAttribute("java.lang:type=OperatingSystem", "AvailableProcessors"));
        metrics.put("system.load", getRemoteAttribute("java.lang:type=OperatingSystem", "SystemLoadAverage"));
        
        // 运行时信息
        metrics.put("uptime", getRemoteAttribute("java.lang:type=Runtime", "Uptime"));
        metrics.put("jvm.name", getRemoteAttribute("java.lang:type=Runtime", "VmName"));
        metrics.put("jvm.version", getRemoteAttribute("java.lang:type=Runtime", "VmVersion"));
    }
    
    /**
     * 获取当前指标
     */
    public Map<String, Object> getCurrentMetrics() {
        return new HashMap<>(lastMetrics);
    }
    
    /**
     * 生成诊断报告
     */
    public ObjectNode generateDiagnosticReport() {
        ObjectNode report = objectMapper.createObjectNode();
        
        report.put("timestamp", System.currentTimeMillis());
        report.put("pid", targetPid > 0 ? targetPid : getCurrentPid());
        
        // 保留顶层字段（兼容旧代码）
        if (lastMetrics.containsKey("jvm.name")) {
            Object val = lastMetrics.get("jvm.name");
            report.put("jvmName", val != null ? val.toString() : "");
        }
        if (lastMetrics.containsKey("jvm.version")) {
            Object val = lastMetrics.get("jvm.version");
            report.put("jvmVersion", val != null ? val.toString() : "");
        }
        if (lastMetrics.containsKey("uptime")) {
            Object val = lastMetrics.get("uptime");
            if (val instanceof Number) {
                report.put("uptime", ((Number) val).longValue());
            }
        }
        
        // 当前指标
        ObjectNode metricsNode = objectMapper.valueToTree(lastMetrics);
        report.set("metrics", metricsNode);
        
        // 检测问题
        Map<String, Object> issues = checkForIssues();
        if (!issues.isEmpty()) {
            ObjectNode issuesNode = objectMapper.valueToTree(issues);
            report.set("issues", issuesNode);
        }
        
        return report;
    }
    
    /**
     * 获取当前进程 PID
     */
    private long getCurrentPid() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(name.split("@")[0]);
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * 检查是否有异常
     */
    public Map<String, Object> checkForIssues() {
        Map<String, Object> issues = new HashMap<>();
        
        Double heapUsage = getDoubleValue(lastMetrics.get("heap.usage"));
        if (heapUsage != null && heapUsage > 0.9) {
            issues.put("high_heap_usage", 
                String.format("Heap usage is high: %.2f%%", heapUsage * 100));
        }
        
        Integer deadlockCount = getIntValue(lastMetrics.get("thread.deadlock"));
        if (deadlockCount != null && deadlockCount > 0) {
            issues.put("deadlock_detected", 
                String.format("Detected %d deadlocked threads", deadlockCount));
        }
        
        return issues;
    }
    
    private Double getDoubleValue(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return null;
    }
    
    private Integer getIntValue(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        return null;
    }
}
