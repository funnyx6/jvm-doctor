package com.github.funnyx6.jvmdoctor.agent;

import com.sun.tools.attach.*;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * 动态挂载工具
 * 
 * 使用方式：
 * java -jar jvm-doctor-agent.jar --pid 12345
 * java -jar jvm-doctor-agent.jar --list
 * java -jar jvm-doctor-agent.jar --help
 */
public class AttachTool {
    
    private static final String AGENT_CLASS = "com.github.funnyx6.jvmdoctor.agent.JvmDoctorAgent";
    
    public static void main(String[] args) {
        String pid = null;
        String serverUrl = null;
        int interval = 30;
        boolean listOnly = false;
        
        // 解析参数
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--pid":
                case "-p":
                    if (i + 1 < args.length) {
                        pid = args[++i];
                    }
                    break;
                case "--url":
                case "-u":
                    if (i + 1 < args.length) {
                        serverUrl = args[++i];
                    }
                    break;
                case "--interval":
                case "-i":
                    if (i + 1 < args.length) {
                        interval = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--list":
                case "-l":
                    listOnly = true;
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    return;
                default:
                    if (arg.startsWith("--")) {
                        System.err.println("Unknown option: " + arg);
                    } else if (pid == null) {
                        pid = arg; // 位置参数
                    }
            }
        }
        
        if (listOnly) {
            listJvms();
            return;
        }
        
        if (pid == null || pid.isEmpty()) {
            System.err.println("Error: PID is required");
            printHelp();
            System.exit(1);
        }
        
        // 验证 PID
        if (!isValidPid(pid)) {
            System.err.println("Error: Invalid PID: " + pid);
            System.exit(1);
        }
        
        // 执行 attach
        attachAgent(pid, serverUrl, interval);
    }
    
    /**
     * 列出所有 Java 进程
     */
    private static void listJvms() {
        System.out.println("Java Processes:");
        System.out.println("---------------");
        
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        for (VirtualMachineDescriptor vm : vms) {
            System.out.printf("  %-8s %s%n", vm.id(), vm.displayName());
        }
        if (vms.isEmpty()) {
            System.out.println("  (no Java processes found)");
        }
    }
    
    /**
     * 验证 PID 是否有效
     */
    private static boolean isValidPid(String pid) {
        try {
            Long.parseLong(pid);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 附加 Agent 到指定进程
     */
    private static void attachAgent(String pid, String serverUrl, int interval) {
        System.out.println("Attaching to PID: " + pid);
        
        VirtualMachine vm = null;
        try {
            // 获取 Agent JAR 路径
            String agentJar = getAgentJarPath();
            System.out.println("Agent JAR: " + agentJar);
            
            // 附加到目标 JVM
            vm = VirtualMachine.attach(pid);
            
            // 构建 Agent 参数
            StringBuilder agentArgs = new StringBuilder();
            if (serverUrl != null) {
                agentArgs.append("server.url=").append(serverUrl);
            }
            if (interval != 30) {
                if (agentArgs.length() > 0) agentArgs.append(",");
                agentArgs.append("report.interval=").append(interval);
            }
            
            String args = agentArgs.length() > 0 ? agentArgs.toString() : null;
            
            // 加载 Agent
            vm.loadAgent(agentJar, args);
            
            System.out.println("Agent attached successfully!");
            System.out.println("Server URL: " + (serverUrl != null ? serverUrl : "http://localhost:8080"));
            System.out.println("Report interval: " + interval + "s");
            
        } catch (AttachNotSupportedException e) {
            System.err.println("Attach not supported: " + e.getMessage());
            System.err.println("");
            System.err.println("Make sure the target JVM has:");
            System.err.println("  -Djdk.attach.allowAttachSelf=true");
            System.err.println("");
            System.err.println("Or add to jvm.conf:");
            System.err.println("  -XX:+AttachListener");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (AgentLoadException e) {
            System.err.println("Agent load error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Failed to attach: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (vm != null) {
                try {
                    vm.detach();
                } catch (IOException e) {
                    // 忽略
                }
            }
        }
    }
    
    /**
     * 获取 Agent JAR 路径
     */
    private static String getAgentJarPath() throws IOException {
        // 获取当前 JAR 的路径
        String path = AttachTool.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        path = java.net.URLDecoder.decode(path, "UTF-8");
        
        // 如果是目录，查找 JAR 文件
        if (path.endsWith("/") || path.endsWith("\\")) {
            java.io.File dir = new java.io.File(path);
            java.io.File[] jars = dir.listFiles((d, name) -> name.startsWith("jvm-doctor-agent") && name.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                return jars[0].getAbsolutePath();
            }
        } else if (path.endsWith(".jar")) {
            return path;
        }
        
        throw new IOException("Cannot find jvm-doctor-agent JAR");
    }
    
    /**
     * 打印帮助信息
     */
    private static void printHelp() {
        System.out.println("JVM Doctor Agent - Dynamic Attach Tool");
        System.out.println("");
        System.out.println("Usage:");
        System.out.println("  java -jar jvm-doctor-agent.jar [options] [pid]");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("  --pid, -p <pid>      Target JVM PID (required if not listing)");
        System.out.println("  --url, -u <url>      Server URL (default: http://localhost:8080)");
        System.out.println("  --interval, -i <sec> Report interval in seconds (default: 30)");
        System.out.println("  --list, -l           List all Java processes");
        System.out.println("  --help, -h           Show this help");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  java -jar jvm-doctor-agent.jar --list");
        System.out.println("  java -jar jvm-doctor-agent.jar --pid 12345");
        System.out.println("  java -jar jvm-doctor-agent.jar -p 12345 -u http://localhost:8080 -i 10");
        System.out.println("  java -jar jvm-doctor-agent.jar 12345");
    }
}
