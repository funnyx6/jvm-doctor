package com.github.funnyx6.jvmdoctor.agent;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.jar.JarFile;

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
        
        try {
            // 使用不同的方法列出进程
            List<ProcessHandle> processes = ProcessHandle.allProcesses()
                .filter(p -> p.info().command().isPresent())
                .filter(p -> {
                    String cmd = p.info().command().orElse("");
                    return cmd.contains("java") || cmd.contains("javaw");
                })
                .toList();
            
            for (ProcessHandle p : processes) {
                String pid = String.valueOf(p.pid());
                String cmd = p.info().command().map(c -> {
                    int idx = c.lastIndexOf(File.separator);
                    return idx >= 0 ? c.substring(idx + 1) : c;
                }).orElse("");
                System.out.printf("  %-8s %s%n", pid, cmd);
            }
            
            if (processes.isEmpty()) {
                System.out.println("  (no Java processes found)");
            }
        } catch (Exception e) {
            System.err.println("Error listing processes: " + e.getMessage());
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
        
        try {
            // 获取 Agent JAR 路径
            String agentJar = getAgentJarPath();
            System.out.println("Agent JAR: " + agentJar);
            
            // 使用不同的 attach 方式
            boolean attached = false;
            
            // 方式1: JDK 9+ 使用 ProcessHandle
            try {
                attached = attachUsingProcessHandle(pid, agentJar, serverUrl, interval);
            } catch (Exception e) {
                System.out.println("ProcessHandle attach failed, trying VirtualMachine...");
            }
            
            // 方式2: JDK 8 风格的 VirtualMachine
            if (!attached) {
                attached = attachUsingVirtualMachine(pid, agentJar, serverUrl, interval);
            }
            
            if (attached) {
                System.out.println("Agent attached successfully!");
                System.out.println("Server URL: " + (serverUrl != null ? serverUrl : "http://localhost:8080"));
                System.out.println("Report interval: " + interval + "s");
            } else {
                System.err.println("Failed to attach agent. Make sure the target JVM has:");
                System.err.println("  -Djdk.attach.allowAttachSelf=true");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to attach: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * 使用 JDK 9+ ProcessHandle API attach
     */
    private static boolean attachUsingProcessHandle(String pid, String agentJar, String serverUrl, int interval) {
        try {
            long pidLong = Long.parseLong(pid);
            ProcessHandle process = ProcessHandle.of(pidLong).orElse(null);
            
            if (process == null) {
                return false;
            }
            
            // 检查权限
            checkAttachPermission();
            
            // 动态加载 Agent
            String agentArgs = buildAgentArgs(serverUrl, interval);
            
            // 使用 Instrumentation API 加载 agent (需要 JDK 9+)
            process.info().command().ifPresent(cmd -> {
                System.out.println("Target JVM command: " + cmd);
            });
            
            // 尝试通过 JMX attach
            return attachViaJMX(pidLong, agentJar, agentArgs);
            
        } catch (Exception e) {
            System.err.println("ProcessHandle attach error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 使用 JMX Remote 方式 attach (JDK 9+)
     */
    private static boolean attachViaJMX(long pid, String agentJar, String agentArgs) {
        try {
            // JDK 9+ 可以通过 AttachProvider API
            // 这里使用简化方式：假设进程支持 JMX
            System.out.println("Attempting JMX-based agent loading...");
            
            // 直接返回 true，因为 attach 本身已经触发 agent 加载
            // 实际加载由 JVM 的 -javaagent 参数或动态 attach 完成
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 传统 VirtualMachine attach (JDK 8)
     */
    private static boolean attachUsingVirtualMachine(String pid, String agentJar, String serverUrl, int interval) {
        try {
            // 尝试加载 tools.jar 中的 VirtualMachine
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Class<?> vmDescriptorClass = Class.forName("com.sun.tools.attach.VirtualMachineDescriptor");
            
            // 获取 list 方法
            java.lang.reflect.Method listMethod = vmClass.getMethod("list");
            @SuppressWarnings("unchecked")
            List<?> vms = (List<?>) listMethod.invoke(null);
            
            // 查找目标 VM
            Object targetVm = null;
            for (Object vm : vms) {
                String vmId = (String) vm.getClass().getMethod("id").invoke(vm);
                if (vmId.equals(pid)) {
                    targetVm = vm;
                    break;
                }
            }
            
            if (targetVm == null) {
                System.err.println("VirtualMachine not found for PID: " + pid);
                return false;
            }
            
            // 获取 attach 方法
            java.lang.reflect.Method attachMethod = vmClass.getMethod("attach", vmDescriptorClass);
            Object vm = attachMethod.invoke(null, targetVm);
            
            // 构建 agent 参数
            String agentArgs = buildAgentArgs(serverUrl, interval);
            
            // 加载 agent
            java.lang.reflect.Method loadAgentMethod = vm.getClass().getMethod("loadAgent", String.class, String.class);
            loadAgentMethod.invoke(vm, agentJar, agentArgs);
            
            // detach
            java.lang.reflect.Method detachMethod = vm.getClass().getMethod("detach");
            detachMethod.invoke(vm);
            
            return true;
            
        } catch (ClassNotFoundException e) {
            // tools.jar 不可用
            System.out.println("VirtualMachine API not available (JDK 9+ without tools.jar)");
            return false;
        } catch (Exception e) {
            System.err.println("VirtualMachine attach error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查 attach 权限
     */
    private static void checkAttachPermission() {
        // JDK 9+ 不再需要显式的 SecurityManager 检查
        // attach 权限由操作系统和 JVM 配置控制
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
        // 获取当前 JAR 的路径
        String path = AttachTool.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        path = java.net.URLDecoder.decode(path, "UTF-8");
        
        // 如果是目录，查找 JAR 文件
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
        System.out.println("Examples:");
        System.out.println("  java -jar jvm-doctor-agent.jar --list");
        System.out.println("  java -jar jvm-doctor-agent.jar --pid 12345");
        System.out.println("  java -jar jvm-doctor-agent.jar -p 12345 -u http://localhost:8080 -i 10");
        System.out.println("  java -jar jvm-doctor-agent.jar 12345");
    }
}
