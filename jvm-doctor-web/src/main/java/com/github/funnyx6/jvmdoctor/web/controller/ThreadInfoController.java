package com.github.funnyx6.jvmdoctor.web.controller;

import com.github.funnyx6.jvmdoctor.web.entity.AppRegistry;
import com.github.funnyx6.jvmdoctor.web.service.AppRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * 线程信息 Controller
 * 提供获取目标应用线程信息的代理 API
 */
@RestController
@RequestMapping("/api/apps")
public class ThreadInfoController {
    
    private final AppRegistryService appRegistryService;
    
    public ThreadInfoController(AppRegistryService appRegistryService) {
        this.appRegistryService = appRegistryService;
    }
    
    /**
     * 获取应用的线程列表
     * GET /api/apps/{appId}/threads
     */
    @GetMapping("/{appId}/threads")
    public ResponseEntity<Map<String, Object>> getThreads(@PathVariable Long appId) {
        return getThreadInfo(appId, "/api/threads");
    }
    
    /**
     * 获取应用的 CPU Top 线程
     * GET /api/apps/{appId}/threads/top
     */
    @GetMapping("/{appId}/threads/top")
    public ResponseEntity<Map<String, Object>> getTopThreads(@PathVariable Long appId) {
        return getThreadInfo(appId, "/api/threads/top");
    }
    
    /**
     * 获取应用的死锁线程
     * GET /api/apps/{appId}/deadlock
     */
    @GetMapping("/{appId}/deadlock")
    public ResponseEntity<Map<String, Object>> getDeadlock(@PathVariable Long appId) {
        return getThreadInfo(appId, "/api/deadlock");
    }
    
    /**
     * 获取指定线程的堆栈信息
     * GET /api/apps/{appId}/threads/{threadId}/stack
     */
    @GetMapping("/{appId}/threads/{threadId}/stack")
    public ResponseEntity<Map<String, Object>> getThreadStack(
            @PathVariable Long appId,
            @PathVariable Long threadId) {
        return getThreadInfo(appId, "/api/threads/" + threadId);
    }
    
    /**
     * 代理请求到目标应用的线程服务器
     */
    private ResponseEntity<Map<String, Object>> getThreadInfo(Long appId, String path) {
        Optional<AppRegistry> appOpt = appRegistryService.getAppById(appId);
        if (appOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        AppRegistry app = appOpt.get();
        Integer threadPort = app.getThreadServerPort();
        
        if (threadPort == null || threadPort <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Thread server not available");
            error.put("message", "This application does not have thread monitoring enabled");
            return ResponseEntity.status(503).body(error);
        }
        
        try {
            String targetUrl = "http://" + app.getHost() + ":" + threadPort + path;
            
            HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                // 读取响应
                Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNext()) {
                    sb.append(scanner.nextLine());
                }
                scanner.close();
                
                // 解析 JSON
                String json = sb.toString();
                Map<String, Object> result = parseJson(json);
                result.put("appId", appId);
                result.put("appName", app.getAppName());
                
                return ResponseEntity.ok(result);
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Failed to connect to thread server");
                error.put("statusCode", responseCode);
                return ResponseEntity.status(responseCode).body(error);
            }
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get thread info");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * 简单 JSON 解析（避免引入 JSON 库）
     */
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        
        // 简单解析 key-value 对
        int i = 0;
        while (i < json.length()) {
            // 找到 key
            while (i < json.length() && json.charAt(i) != '"') i++;
            if (i >= json.length()) break;
            i++; // 跳过 "
            
            StringBuilder key = new StringBuilder();
            while (i < json.length() && json.charAt(i) != '"') {
                key.append(json.charAt(i++));
            }
            i++; // 跳过 "
            
            // 找到 :
            while (i < json.length() && json.charAt(i) != ':') i++;
            if (i >= json.length()) break;
            i++; // 跳过 :
            
            // 跳过空白
            while (i < json.length() && json.charAt(i) == ' ') i++;
            
            // 解析 value
            if (i < json.length() && json.charAt(i) == '{') {
                // 嵌套对象
                int braceCount = 0;
                int start = i;
                for (int j = i; j < json.length(); j++) {
                    if (json.charAt(j) == '{') braceCount++;
                    else if (json.charAt(j) == '}') braceCount--;
                    if (braceCount == 0) {
                        i = j + 1;
                        break;
                    }
                }
                String nested = json.substring(start, i);
                result.put(key.toString(), parseJson(nested));
            } else if (i < json.length() && json.charAt(i) == '[') {
                // 数组
                int braceCount = 0;
                int start = i;
                for (int j = i; j < json.length(); j++) {
                    if (json.charAt(j) == '[') braceCount++;
                    else if (json.charAt(j) == ']') braceCount--;
                    if (braceCount == 0) {
                        i = j + 1;
                        break;
                    }
                }
                result.put(key.toString(), json.substring(start, i));
            } else if (i < json.length() && json.charAt(i) == '"') {
                // 字符串
                i++;
                StringBuilder value = new StringBuilder();
                while (i < json.length() && json.charAt(i) != '"') {
                    value.append(json.charAt(i++));
                }
                i++; // 跳过 "
                result.put(key.toString(), value.toString());
            } else {
                // 数字或布尔值
                StringBuilder value = new StringBuilder();
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}' && json.charAt(i) != ']') {
                    value.append(json.charAt(i++));
                }
                String valStr = value.toString().trim();
                if (valStr.equals("true")) {
                    result.put(key.toString(), true);
                } else if (valStr.equals("false")) {
                    result.put(key.toString(), false);
                } else if (valStr.equals("null")) {
                    result.put(key.toString(), null);
                } else {
                    // 尝试解析为数字
                    try {
                        if (valStr.contains(".")) {
                            result.put(key.toString(), Double.parseDouble(valStr));
                        } else {
                            result.put(key.toString(), Long.parseLong(valStr));
                        }
                    } catch (NumberFormatException e) {
                        result.put(key.toString(), valStr);
                    }
                }
            }
            
            // 跳过 , 和空白
            while (i < json.length() && (json.charAt(i) == ',' || json.charAt(i) == ' ' || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) i++;
        }
        
        return result;
    }
}
