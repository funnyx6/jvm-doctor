package com.github.funnyx6.jvmdoctor.agent;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 线程信息 HTTP Server
 * 在目标应用内提供线程信息 API
 */
public class ThreadServer {
    
    private static volatile HttpServer server;
    private static volatile int port = 0; // 0 表示随机端口
    
    /**
     * 启动 HTTP 服务器
     * 
     * @param port 端口，0 表示随机端口
     * @return 实际启动的端口
     */
    public static synchronized int start(int port) {
        if (server != null) {
            return port;
        }
        
        try {
            ThreadServer.port = port;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // 注册线程相关 API
            server.createContext("/api/threads", new ThreadHandler());
            server.createContext("/api/deadlock", new ThreadHandler());
            
            // 健康检查
            server.createContext("/api/health", exchange -> {
                String response = "{\"status\":\"ok\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            
            int actualPort = server.getAddress().getPort();
            System.out.println("[ThreadServer] Started on port " + actualPort);
            
            return actualPort;
            
        } catch (IOException e) {
            System.err.println("[ThreadServer] Failed to start: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 停止服务器
     */
    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("[ThreadServer] Stopped");
        }
    }
    
    /**
     * 获取服务器实例
     */
    public static HttpServer getServer() {
        return server;
    }
    
    /**
     * 获取服务器地址
     */
    public static String getAddress() {
        if (server == null) {
            return null;
        }
        return "http://localhost:" + server.getAddress().getPort();
    }
}
