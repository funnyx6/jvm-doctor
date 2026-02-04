package com.github.funnyx6.jvmdoctor.agent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 动态挂载工具
 * 
 * 使用方式：
 * java -jar jvm-doctor-agent.jar --pid 12345
 * java -jar jvm-doctor-agent.jar --list
 * java -jar jvm-doctor-agent.jar --help
 * 
 * 注意：JDK 8 需要 tools.jar 在 classpath 中
 */
public class AttachTool {
    
    private static final String VIRTUAL_MACHINE_CLASS = "com.sun.tools.attach.VirtualMachine";
    private static final String VIRTUAL_MACHINE_DESCRIPTOR_CLASS = "com.sun.tools.attach.VirtualMachineDescriptor";
    
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
        
        try {
            // 加载 VirtualMachine.list() 方法
            Class<?> vmClass = Class.forName(VIRTUAL_MACHINE_CLASS);
            Method listMethod = vmClass.getMethod("list");
            @SuppressWarnings("unchecked")
            List<Object> vms = (List<Object>) listMethod.invoke(null);
            
            if (vms == null || vms.isEmpty()) {
                System.out.println("  (no Java processes found)");
                return;
            }
            
            for (Object vm : vms) {
                String id = (String) vm.getClass().getMethod("id").invoke(vm);
                String displayName = (String) vm.getClass().getMethod("displayName").invoke(vm);
                if (displayName == null || displayName.isEmpty()) {
                    displayName = "(no name)";
                }
                System.out.printf("  %-8s %s%n", id, displayName);
            }
            
        } catch (ClassNotFoundException e) {
            System.err.println("Attach API not available. Make sure tools.jar is in classpath.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error listing JVMs: " + e.getMessage());
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
        
        Object vm = null;
        try {
            // 加载 VirtualMachine 类
            Class<?> vmClass = Class.forName(VIRTUAL_MACHINE_CLASS);
            Class<?> vmDescriptorClass = Class.forName(VIRTUAL_MACHINE_DESCRIPTOR_CLASS);
            
            // 获取 attach 方法
            Method attachMethod = vmClass.getMethod("attach", vmDescriptorClass);
            
            // 获取目标 VM
            Method listMethod = vmClass.getMethod("list");
            @SuppressWarnings("unchecked")
            List<Object> vms = (List<Object>) listMethod.invoke(null);
            
            Object targetVm = null;
            for (Object candidate : vms) {
                String id = (String) candidate.getClass().getMethod("id").invoke(candidate);
                if (id.equals(pid)) {
                    targetVm = candidate;
                    break;
                }
            }
            
            if (targetVm == null) {
                System.err.println("VirtualMachine not found for PID: " + pid);
                System.exit(1);
            }
            
            // 获取 Agent JAR 路径
            String agentJar = getAgentJarPath();
            System.out.println("Agent JAR: " + agentJar);
            
            // 执行 attach
            vm = attachMethod.invoke(null, targetVm);
            
            // 构建 Agent 参数
            String agentArgs = buildAgentArgs(serverUrl, interval);
            
            // 加载 agent
            Method loadAgentMethod = vm.getClass().getMethod("loadAgent", String.class, String.class);
            loadAgentMethod.invoke(vm, agentJar, agentArgs);
            
            System.out.println("Agent attached successfully!");
            System.out.println("Server URL: " + (serverUrl != null ? serverUrl : "http://localhost:8080"));
            System.out.println("Report interval: " + interval + "s");
            
        } catch (ClassNotFoundException e) {
            System.err.println("Attach API not available. Make sure tools.jar is in classpath.");
            System.err.println("");
            System.err.println("For JDK 8, ensure JAVA_HOME points to JDK 8.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Failed to attach: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (vm != null) {
                try {
                    Method detachMethod = vm.getClass().getMethod("detach");
                    detachMethod.invoke(vm);
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
    }
    
    /**
     * 构建 Agent 参数
     */
    private static String buildAgentArgs(String serverUrl, int interval) {
        StringBuilder sb = new StringBuilder();
        if (serverUrl != null) {
            sb.append("server.url=").append(serverUrl);
        }
        if (interval != 30) {
            if (sb.length() > 0) sb.append(",");
            sb.append("report.interval=").append(interval);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
    
    /**
     * 获取 Agent JAR 路径
     */
    private static String getAgentJarPath() throws IOException {
        String path = AttachTool.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        path = java.net.URLDecoder.decode(path, "UTF-8");
        
        if (path.endsWith("/") || path.endsWith("\\")) {
            File dir = new File(path);
            File[] jars = dir.listFiles((d, name) -> 
                name.startsWith("jvm-doctor-agent") && name.endsWith(".jar"));
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
        System.out.println("Requirements:");
        System.out.println("  JDK 8 with tools.jar in classpath");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  java -jar jvm-doctor-agent.jar --list");
        System.out.println("  java -jar jvm-doctor-agent.jar --pid 12345");
        System.out.println("  java -cp tools.jar:jvm-doctor-agent-attach.jar AttachTool --pid 12345");
    }
}
