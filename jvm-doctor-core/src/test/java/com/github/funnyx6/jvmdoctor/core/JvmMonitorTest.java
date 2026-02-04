package com.github.funnyx6.jvmdoctor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JvmMonitor 单元测试
 */
class JvmMonitorTest {
    
    private JvmMonitor monitor;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        monitor = new JvmMonitor();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testCollectMetrics() {
        // 测试指标收集
        monitor.collectMetrics();
        
        Map<String, Object> metrics = monitor.getCurrentMetrics();
        
        assertNotNull(metrics);
        assertFalse(metrics.isEmpty());
        
        // 验证基本指标存在
        assertTrue(metrics.containsKey("heap.used"));
        assertTrue(metrics.containsKey("heap.committed"));
        assertTrue(metrics.containsKey("heap.max"));
        assertTrue(metrics.containsKey("thread.count"));
        assertTrue(metrics.containsKey("uptime"));
    }
    
    @Test
    void testGenerateDiagnosticReport() {
        // 测试诊断报告生成
        monitor.collectMetrics();
        com.fasterxml.jackson.databind.node.ObjectNode report = monitor.generateDiagnosticReport();
        
        assertNotNull(report);
        assertNotNull(report.get("timestamp"));
        assertNotNull(report.get("pid"));
        assertNotNull(report.get("metrics"));
    }
    
    @Test
    void testCheckForIssues() {
        // 测试问题检测
        monitor.collectMetrics();
        Map<String, Object> issues = monitor.checkForIssues();
        
        assertNotNull(issues);
        // 正常情况下不应该有严重问题
    }
    
    @Test
    void testMemoryMetricsExist() {
        // 测试内存指标完整性
        monitor.collectMetrics();
        Map<String, Object> metrics = monitor.getCurrentMetrics();
        
        // 堆内存指标
        assertTrue(metrics.containsKey("heap.used"));
        assertTrue(metrics.containsKey("heap.committed"));
        assertTrue(metrics.containsKey("heap.max"));
        assertTrue(metrics.containsKey("heap.usage"));
        
        // 非堆内存指标
        assertTrue(metrics.containsKey("nonheap.used"));
        assertTrue(metrics.containsKey("nonheap.committed"));
        assertTrue(metrics.containsKey("nonheap.max"));
    }
    
    @Test
    void testThreadMetricsExist() {
        // 测试线程指标完整性
        monitor.collectMetrics();
        Map<String, Object> metrics = monitor.getCurrentMetrics();
        
        assertTrue(metrics.containsKey("thread.count"));
        assertTrue(metrics.containsKey("thread.daemon"));
        assertTrue(metrics.containsKey("thread.peak"));
        assertTrue(metrics.containsKey("thread.totalStarted"));
        assertTrue(metrics.containsKey("thread.deadlock"));
    }
    
    @Test
    void testRuntimeMetricsExist() {
        // 测试运行时指标完整性
        monitor.collectMetrics();
        Map<String, Object> metrics = monitor.getCurrentMetrics();
        
        assertTrue(metrics.containsKey("uptime"));
        assertTrue(metrics.containsKey("startTime"));
        assertTrue(metrics.containsKey("jvm.name"));
        assertTrue(metrics.containsKey("jvm.version"));
    }
    
    @Test
    void testMultipleCollectCalls() {
        // 测试多次收集
        monitor.collectMetrics();
        Map<String, Object> metrics1 = monitor.getCurrentMetrics();
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        monitor.collectMetrics();
        Map<String, Object> metrics2 = monitor.getCurrentMetrics();
        
        // uptime 应该增加
        long uptime1 = (Long) metrics1.get("uptime");
        long uptime2 = (Long) metrics2.get("uptime");
        assertTrue(uptime2 >= uptime1);
    }
    
    @Test
    void testStartAndStopMonitoring() {
        // 测试监控启动和停止
        assertDoesNotThrow(() -> {
            monitor.startMonitoring(5);
            Thread.sleep(1000); // 运行 1 秒
            monitor.stopMonitoring();
        });
    }
    
    @Test
    void testHeapUsageCalculation() {
        // 测试堆使用率计算
        monitor.collectMetrics();
        Map<String, Object> metrics = monitor.getCurrentMetrics();
        
        Object usage = metrics.get("heap.usage");
        assertNotNull(usage);
        
        // 使用率应该在 0-1 之间
        double usageValue = ((Number) usage).doubleValue();
        assertTrue(usageValue >= 0 && usageValue <= 1);
    }
}
