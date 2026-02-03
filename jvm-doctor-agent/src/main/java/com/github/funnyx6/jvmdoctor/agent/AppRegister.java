package com.github.funnyx6.jvmdoctor.agent;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用注册模块
 * 负责向 Server 注册应用信息
 */
public class AppRegister {
    
    private final AgentConfig config;
    private Long appId;
    
    public AppRegister(AgentConfig config) {
        this.config = config;
    }
    
    /**
     * 执行注册
     * 
     * @return 注册后的应用 ID
     */
    public Long register() {
        try {
            String registerUrl = config.getServerUrl() + "/api/apps/register";
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("appName", config.getAppName());
            requestBody.put("host", config.getAppHost());
            requestBody.put("port", config.getAppPort());
            requestBody.put("jvmName", System.getProperty("java.vm.name", "Unknown"));
            requestBody.put("jvmVersion", System.getProperty("java.version", "Unknown"));
            requestBody.put("startTime", System.currentTimeMillis());
            
            String json = toJson(requestBody);
            
            // 发送 POST 请求
            HttpURLConnection conn = (HttpURLConnection) new URL(registerUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String response = readResponse(conn);
                this.appId = extractAppId(response);
                System.out.println("[AppRegister] Registered successfully, appId: " + appId);
                return appId;
            } else {
                System.err.println("[AppRegister] Failed to register, response code: " + responseCode);
                String error = readResponse(conn);
                System.err.println("[AppRegister] Error: " + error);
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("[AppRegister] Failed to register: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 发送心跳
     */
    public boolean heartbeat() {
        if (appId == null) {
            System.err.println("[AppRegister] App not registered, cannot heartbeat");
            return false;
        }
        
        try {
            String heartbeatUrl = config.getServerUrl() + "/api/apps/" + appId + "/heartbeat";
            
            HttpURLConnection conn = (HttpURLConnection) new URL(heartbeatUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                return true;
            } else {
                System.err.println("[AppRegister] Heartbeat failed, response code: " + responseCode);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("[AppRegister] Heartbeat error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 发送下线通知
     */
    public boolean offline() {
        if (appId == null) {
            return true; // 未注册过，视为成功
        }
        
        try {
            String offlineUrl = config.getServerUrl() + "/api/apps/" + appId + "/offline";
            
            HttpURLConnection conn = (HttpURLConnection) new URL(offlineUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("[AppRegister] Sent offline notification");
                return true;
            } else {
                System.err.println("[AppRegister] Offline notification failed: " + responseCode);
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("[AppRegister] Offline error: " + e.getMessage());
            return false;
        }
    }
    
    public Long getAppId() {
        return appId;
    }
    
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }
    
    private String readResponse(HttpURLConnection conn) {
        try {
            java.io.InputStream is = conn.getInputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    private Long extractAppId(String json) {
        // 简单解析 {"appId": 1, ...}
        try {
            int start = json.indexOf("\"appId\":");
            if (start >= 0) {
                start += 8;
                int end = json.indexOf(",", start);
                if (end < 0) end = json.indexOf("}", start);
                String idStr = json.substring(start, end).trim();
                return Long.parseLong(idStr);
            }
        } catch (Exception e) {
            // 解析失败
        }
        return null;
    }
}
