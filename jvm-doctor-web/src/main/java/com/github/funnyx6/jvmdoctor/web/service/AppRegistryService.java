package com.github.funnyx6.jvmdoctor.web.service;

import com.github.funnyx6.jvmdoctor.web.entity.AppRegistry;
import com.github.funnyx6.jvmdoctor.web.repository.AppRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AppRegistryService {
    
    private static final Logger logger = LoggerFactory.getLogger(AppRegistryService.class);
    
    private final AppRegistryRepository repository;
    
    public AppRegistryService(AppRegistryRepository repository) {
        this.repository = repository;
    }
    
    /**
     * 注册应用
     */
    @Transactional
    public AppRegistry register(AppRegistry app) {
        // 检查是否已存在
        Optional<AppRegistry> existing = repository.findByAppNameAndHostAndPort(
                app.getAppName(), app.getHost(), app.getPort());
        
        if (existing.isPresent()) {
            // 更新心跳时间
            AppRegistry existingApp = existing.get();
            existingApp.setLastHeartbeat(Instant.now().toEpochMilli());
            existingApp.setStatus("running");
            logger.info("App already registered, updating heartbeat: {}", app.getAppName());
            return repository.save(existingApp);
        }
        
        // 新注册
        app.setRegisteredAt(Instant.now().toEpochMilli());
        app.setLastHeartbeat(Instant.now().toEpochMilli());
        app.setStatus("running");
        logger.info("Registering new app: {}", app.getAppName());
        return repository.save(app);
    }
    
    /**
     * 更新心跳
     */
    @Transactional
    public void heartbeat(Long appId) {
        repository.findById(appId).ifPresent(app -> {
            app.setLastHeartbeat(Instant.now().toEpochMilli());
            app.setStatus("running");
            repository.save(app);
        });
    }
    
    /**
     * 下线应用
     */
    @Transactional
    public void offline(Long appId) {
        repository.findById(appId).ifPresent(app -> {
            app.setStatus("offline");
            repository.save(app);
            logger.info("App went offline: {}", app.getAppName());
        });
    }
    
    /**
     * 获取所有应用
     */
    public List<AppRegistry> getAllApps() {
        return repository.findAllByOrderByRegisteredAtDesc();
    }
    
    /**
     * 获取运行中的应用
     */
    public List<AppRegistry> getRunningApps() {
        return repository.findByStatus("running");
    }
    
    /**
     * 根据 ID 获取应用
     */
    public Optional<AppRegistry> getAppById(Long id) {
        return repository.findById(id);
    }
    
    /**
     * 标记离线应用（心跳超时检测）
     */
    @Transactional
    public void markOfflineApps(long timeoutMs) {
        long threshold = Instant.now().toEpochMilli() - timeoutMs;
        List<AppRegistry> apps = repository.findByStatus("running");
        
        for (AppRegistry app : apps) {
            if (app.getLastHeartbeat() != null && app.getLastHeartbeat() < threshold) {
                app.setStatus("offline");
                repository.save(app);
                logger.warn("App heartbeat timeout, marked offline: {}", app.getAppName());
            }
        }
    }
}
