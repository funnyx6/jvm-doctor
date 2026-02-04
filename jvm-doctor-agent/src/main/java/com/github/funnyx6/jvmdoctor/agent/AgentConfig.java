package com.github.funnyx6.jvmdoctor.agent;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Properties;

/**
 * Agent 配置管理
 * 
 * 配置优先级（从高到低）：
 * 1. JVM 启动参数 -Djvm-doctor.xxx
 * 2. 配置文件 jvm-doctor-agent.properties
 * 3. 命令行参数
 * 4. 默认值
 */
public class AgentConfig {
    
    private String serverUrl = "http://localhost:8080";
    private int reportInterval = 30;  // 秒
    private String appName = "";
    private String appHost = "";
    private int appPort = 0;
    private int threadServerPort = 0; // 线程服务器端口
    
    public AgentConfig() {
    }
    
    /**
     * 解析配置
     */
    public static AgentConfig parse(String args) {
        AgentConfig config = new AgentConfig();
        
        // 1. 从系统属性读取（最高优先级）
        config.serverUrl = getSystemProperty("jvm-doctor.server.url", config.serverUrl);
        config.reportInterval = Integer.parseInt(
                getSystemProperty("jvm-doctor.report.interval", String.valueOf(config.reportInterval)));
        config.appName = getSystemProperty("jvm-doctor.app.name", config.appName);
        config.appHost = getSystemProperty("jvm-doctor.app.host", config.appHost);
        
        // 2. 从配置文件读取
        config.loadFromPropertiesFile();
        
        // 3. 解析命令行参数
        if (args != null && !args.isEmpty()) {
            config.parseArgs(args);
        }
        
        // 4. 自动检测未配置的值
        config.autoDetect();
        
        return config;
    }
    
    /**
     * 解析命令行参数字符串
     * 格式：key=value,key=value
     */
    private void parseArgs(String args) {
        String[] pairs = args.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                switch (key.toLowerCase()) {
                    case "server.url":
                    case "server":
                        this.serverUrl = value;
                        break;
                    case "interval":
                    case "report.interval":
                        this.reportInterval = Integer.parseInt(value);
                        break;
                    case "app.name":
                        this.appName = value;
                        break;
                    case "app.host":
                        this.appHost = value;
                        break;
                }
            }
        }
    }
    
    /**
     * 从属性文件加载配置
     */
    private void loadFromPropertiesFile() {
        try {
            Properties props = new Properties();
            // 尝试加载当前目录的配置文件
            java.io.FileInputStream fis = new java.io.FileInputStream("jvm-doctor-agent.properties");
            props.load(fis);
            fis.close();
            
            this.serverUrl = props.getProperty("server.url", this.serverUrl);
            this.reportInterval = Integer.parseInt(
                    props.getProperty("report.interval", String.valueOf(this.reportInterval)));
            this.appName = props.getProperty("app.name", this.appName);
            this.appHost = props.getProperty("app.host", this.appHost);
            
        } catch (Exception e) {
            // 配置文件不存在或读取失败，使用默认值
        }
    }
    
    /**
     * 自动检测配置
     */
    private void autoDetect() {
        // 自动检测应用名称
        if (appName == null || appName.isEmpty()) {
            // 获取当前进程的命令行或 jar 包名
            String mainClass = System.getProperty("sun.java.command");
            if (mainClass != null) {
                // 提取简单名称
                String[] parts = mainClass.split("\\s+");
                appName = parts[0];
                int lastSlash = appName.lastIndexOf('/');
                if (lastSlash >= 0) {
                    appName = appName.substring(lastSlash + 1);
                }
                int jarIndex = appName.lastIndexOf(".jar");
                if (jarIndex > 0) {
                    appName = appName.substring(0, jarIndex);
                }
            }
            if (appName == null || appName.isEmpty()) {
                appName = "unknown-" + getCurrentPid();
            }
        }
        
        // 自动检测主机地址
        if (appHost == null || appHost.isEmpty()) {
            try {
                java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
                appHost = localHost.getHostAddress();
            } catch (Exception e) {
                appHost = "localhost";
            }
        }
        
        // 自动检测端口（尝试从系统属性读取）
        if (appPort <= 0) {
            String portStr = System.getProperty("server.port", 
                          System.getProperty("PORT", "0"));
            try {
                appPort = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                appPort = 0;
            }
        }
    }
    
    private static String getSystemProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
    
    /**
     * 获取当前进程 ID（JDK 8 兼容方式）
     */
    private static String getCurrentPid() {
        try {
            // JDK 8: 通过 RuntimeMXBean 获取
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            String name = runtimeBean.getName();
            int atIndex = name.indexOf('@');
            if (atIndex > 0) {
                return name.substring(0, atIndex);
            }
        } catch (Exception e) {
            // 忽略
        }
        return "0";
    }
    
    // Getters
    public String getServerUrl() { return serverUrl; }
    public int getReportInterval() { return reportInterval; }
    public String getAppName() { return appName; }
    public String getAppHost() { return appHost; }
    public int getAppPort() { return appPort; }
    public int getThreadServerPort() { return threadServerPort; }
    public void setThreadServerPort(int port) { this.threadServerPort = port; }
    
    @Override
    public String toString() {
        return "AgentConfig{" +
                "serverUrl='" + serverUrl + '\'' +
                ", reportInterval=" + reportInterval +
                ", appName='" + appName + '\'' +
                ", appHost='" + appHost + '\'' +
                ", appPort=" + appPort +
                ", threadServerPort=" + threadServerPort +
                '}';
    }
}
