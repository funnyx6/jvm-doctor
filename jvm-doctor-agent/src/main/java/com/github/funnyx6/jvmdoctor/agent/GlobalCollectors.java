package com.github.funnyx6.jvmdoctor.agent;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义指标采集器注册表
 * 
 * 使用方式：
 * 1. 实现 MetricsCollector 接口
 * 2. 注册到 GlobalCollectors
 * 3. 自动随 Agent 上报
 */
public class GlobalCollectors {
    
    /**
     * 自定义指标采集器接口
     */
    public interface CustomCollector {
        /**
         * 采集自定义指标
         * @return 指标 Map，key 为指标名，value 为指标值
         */
        Map<String, Object> collect();
        
        /**
         * 获取分类名称（用于组织指标）
         */
        String getCategory();
    }
    
    private static final Map<String, CustomCollector> collectors = new ConcurrentHashMap<>();
    
    /**
     * 注册自定义采集器
     */
    public static void register(String name, CustomCollector collector) {
        collectors.put(name, collector);
        System.out.println("[GlobalCollectors] Registered collector: " + name);
    }
    
    /**
     * 注销采集器
     */
    public static void unregister(String name) {
        collectors.remove(name);
        System.out.println("[GlobalCollectors] Unregistered collector: " + name);
    }
    
    /**
     * 获取所有采集器
     */
    public static Map<String, CustomCollector> getCollectors() {
        return collectors;
    }
    
    /**
     * 收集所有自定义指标
     */
    public static Map<String, Object> collectAll() {
        Map<String, Object> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, CustomCollector> entry : collectors.entrySet()) {
            try {
                Map<String, Object> metrics = entry.getValue().collect();
                if (metrics != null && !metrics.isEmpty()) {
                    // 按分类组织指标
                    String category = entry.getValue().getCategory();
                    for (Map.Entry<String, Object> metric : metrics.entrySet()) {
                        result.put(category + "." + metric.getKey(), metric.getValue());
                    }
                }
            } catch (Exception e) {
                System.err.println("[GlobalCollectors] Collector failed: " + entry.getKey() + " - " + e.getMessage());
            }
        }
        return result;
    }
    
    /**
     * 打印所有已注册的采集器
     */
    public static void printRegistered() {
        System.out.println("[GlobalCollectors] Registered collectors:");
        if (collectors.isEmpty()) {
            System.out.println("  (none)");
        } else {
            collectors.keySet().forEach(name -> 
                System.out.println("  - " + name));
        }
    }
}
