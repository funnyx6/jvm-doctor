package com.github.funnyx6.jvmdoctor.web.controller;

import com.github.funnyx6.jvmdoctor.web.dto.AppRegisterRequest;
import com.github.funnyx6.jvmdoctor.web.dto.AppRegisterResponse;
import com.github.funnyx6.jvmdoctor.web.entity.AppRegistry;
import com.github.funnyx6.jvmdoctor.web.service.AppRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps")
public class AppController {
    
    private final AppRegistryService appRegistryService;
    
    public AppController(AppRegistryService appRegistryService) {
        this.appRegistryService = appRegistryService;
    }
    
    /**
     * 注册应用
     * POST /api/apps/register
     */
    @PostMapping("/register")
    public ResponseEntity<AppRegisterResponse> register(@RequestBody AppRegisterRequest request) {
        AppRegistry app = new AppRegistry();
        app.setAppName(request.getAppName());
        app.setHost(request.getHost());
        app.setPort(request.getPort());
        app.setJvmName(request.getJvmName());
        app.setJvmVersion(request.getJvmVersion());
        app.setStartTime(request.getStartTime());
        
        AppRegistry saved = appRegistryService.register(app);
        
        AppRegisterResponse response = new AppRegisterResponse(
                saved.getId(),
                saved.getStatus(),
                "App registered successfully"
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 心跳
     * POST /api/apps/{appId}/heartbeat
     */
    @PostMapping("/{appId}/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(@PathVariable Long appId) {
        appRegistryService.heartbeat(appId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Heartbeat received");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 下线应用
     * POST /api/apps/{appId}/offline
     */
    @PostMapping("/{appId}/offline")
    public ResponseEntity<Map<String, Object>> offline(@PathVariable Long appId) {
        appRegistryService.offline(appId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "App marked as offline");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取所有应用
     * GET /api/apps
     */
    @GetMapping
    public ResponseEntity<List<AppRegistry>> getAllApps() {
        return ResponseEntity.ok(appRegistryService.getAllApps());
    }
    
    /**
     * 获取运行中的应用
     * GET /api/apps/running
     */
    @GetMapping("/running")
    public ResponseEntity<List<AppRegistry>> getRunningApps() {
        return ResponseEntity.ok(appRegistryService.getRunningApps());
    }
    
    /**
     * 获取应用详情
     * GET /api/apps/{appId}
     */
    @GetMapping("/{appId}")
    public ResponseEntity<AppRegistry> getApp(@PathVariable Long appId) {
        return appRegistryService.getAppById(appId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
