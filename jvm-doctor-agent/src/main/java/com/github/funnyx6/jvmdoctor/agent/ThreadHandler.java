package com.github.funnyx6.jvmdoctor.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 线程信息 HTTP Handler
 * 提供线程列表、CPU Top、线程堆栈等 API
 */
public class ThreadHandler implements HttpHandler {
    
    private final MetricsCollector collector;
    
    public ThreadHandler() {
        this.collector = new MetricsCollector();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        // 添加 CORS 头
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        
        String response;
        int statusCode = 200;
        
        try {
            if (path.endsWith("/threads")) {
                // 获取所有线程
                response = getAllThreads();
            } else if (path.endsWith("/threads/top")) {
                // 获取 CPU Top 线程
                String[] parts = path.split("/");
                int topN = 10;
                try {
                    if (parts.length > 0) {
                        topN = Integer.parseInt(parts[parts.length - 1]);
                    }
                } catch (NumberFormatException e) {
                    // 使用默认值
                }
                response = getTopCpuThreads(topN);
            } else if (path.startsWith("/threads/") && !path.endsWith("/threads")) {
                // 获取指定线程堆栈
                String[] parts = path.split("/");
                if (parts.length >= 3) {
                    try {
                        long threadId = Long.parseLong(parts[parts.length - 1]);
                        response = getThreadStack(threadId);
                    } catch (NumberFormatException e) {
                        response = "{\"error\":\"Invalid thread ID\"}";
                        statusCode = 400;
                    }
                } else {
                    response = "{\"error\":\"Invalid path\"}";
                    statusCode = 400;
                }
            } else if (path.endsWith("/deadlock")) {
                // 获取死锁线程
                response = getDeadlockedThreads();
            } else {
                response = "{\"error\":\"Not found\"}";
                statusCode = 404;
            }
        } catch (Exception e) {
            response = "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            statusCode = 500;
        }
        
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    /**
     * 获取所有线程信息
     */
    private String getAllThreads() {
        List<Map<String, Object>> threads = collector.collectThreadInfo();
        
        // 统计各状态线程数
        Map<String, Integer> stateCounts = new HashMap<>();
        for (Map<String, Object> thread : threads) {
            String state = (String) thread.get("state");
            stateCounts.merge(state, 1, Integer::sum);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("threads", threads);
        result.put("totalCount", threads.size());
        result.put("stateCounts", stateCounts);
        
        return toJson(result);
    }
    
    /**
     * 获取 CPU 占用 Top 线程
     */
    private String getTopCpuThreads(int topN) {
        List<Map<String, Object>> threads = collector.getTopCpuThreads(topN);
        
        Map<String, Object> result = new HashMap<>();
        result.put("threads", threads);
        result.put("count", threads.size());
        
        return toJson(result);
    }
    
    /**
     * 获取指定线程的堆栈信息
     */
    private String getThreadStack(long threadId) {
        Map<String, Object> thread = collector.getThreadStack(threadId);
        return toJson(thread);
    }
    
    /**
     * 获取死锁线程列表
     */
    private String getDeadlockedThreads() {
        List<Map<String, Object>> deadlocks = collector.getDeadlockedThreads();
        
        Map<String, Object> result = new HashMap<>();
        result.put("deadlocks", deadlocks);
        result.put("count", deadlocks.size());
        result.put("hasDeadlock", !deadlocks.isEmpty());
        
        return toJson(result);
    }
    
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                sb.append(toJson((List<?>) value));
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number) {
                sb.append(value);
            } else if (value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String toJson(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Object value = list.get(i);
            if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else {
                sb.append(value);
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
