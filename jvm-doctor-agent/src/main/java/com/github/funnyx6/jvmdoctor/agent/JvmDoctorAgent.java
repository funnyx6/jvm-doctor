package com.github.funnyx6.jvmdoctor.agent;

import java.lang.instrument.Instrumentation;

/**
 * JVM Doctor Agent 入口
 * 
 * 使用方式：
 * java -javaagent:jvm-doctor-agent.jar -jar your-app.jar
 * 
 * 或动态挂载：
 * java -jar jvm-doctor-agent.jar --pid 12345
 */
public class JvmDoctorAgent {
    
    /**
     * premain 方式（静态加载，Java Agent 标准入口）
     */
    public static void premain(String args, Instrumentation inst) {
        System.out.println("[JvmDoctorAgent] premain called with args: " + args);
        startAgent(args);
    }
    
    /**
     * agentmain 方式（动态挂载）
     */
    public static void agentmain(String args, Instrumentation inst) {
        System.out.println("[JvmDoctorAgent] agentmain called with args: " + args);
        startAgent(args);
    }
    
    /**
     * 启动 Agent 主逻辑
     */
    private static void startAgent(String args) {
        try {
            // 解析配置
            AgentConfig config = AgentConfig.parse(args);
            
            System.out.println("[JvmDoctorAgent] Server URL: " + config.getServerUrl());
            System.out.println("[JvmDoctorAgent] Report Interval: " + config.getReportInterval() + "s");
            
            // 启动指标上报
            MetricsReporter.start(config);
            
            System.out.println("[JvmDoctorAgent] Agent started successfully");
            
        } catch (Exception e) {
            System.err.println("[JvmDoctorAgent] Failed to start agent: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
