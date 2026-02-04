package com.github.funnyx6.jvmdoctor.cli;

import com.github.funnyx6.jvmdoctor.core.JvmMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Command(name = "jvm-doctor", 
         mixinStandardHelpOptions = true,
         version = "1.0.0",
         description = "JVM diagnostic and monitoring tool")
public class JvmDoctorCli implements Callable<Integer> {
    
    @Option(names = {"-v", "--verbose"}, 
            description = "Verbose mode")
    private boolean verbose;
    
    @Command(name = "monitor", 
             description = "Monitor JVM metrics in real-time")
    static class MonitorCommand implements Callable<Integer> {
        
        @Option(names = {"-i", "--interval"}, 
                description = "Monitoring interval in seconds (default: 5)",
                defaultValue = "5")
        private int interval;
        
        @Option(names = {"-d", "--duration"}, 
                description = "Monitoring duration in seconds (0 for infinite)",
                defaultValue = "0")
        private int duration;
        
        @Option(names = {"-o", "--output"}, 
                description = "Output file for metrics (JSON format)")
        private File outputFile;
        
        @Option(names = {"-p", "--pid"}, 
                description = "Target JVM PID",
                required = false)
        private String pid;
        
        @Option(names = {"--current"}, 
                description = "Monitor the current JVM process (default behavior)",
                required = false,
                hidden = true)
        private boolean currentProcess;
        
        private long getPidValue() {
            if (pid != null && !pid.trim().isEmpty()) {
                try {
                    return Long.parseLong(pid.trim());
                } catch (NumberFormatException e) {
                    System.err.println("Invalid PID: " + pid);
                }
            }
            return -1; // 默认监控当前进程
        }
        
        @Override
        public Integer call() throws Exception {
            System.out.println("Starting JVM monitoring...");
            System.out.println("Interval: " + interval + " seconds");
            if (duration > 0) {
                System.out.println("Duration: " + duration + " seconds");
            }
            
            // 如果指定了 PID，连接远程 JVM
            JvmMonitor monitor;
            long targetPid = getPidValue();
            if (targetPid > 0) {
                System.out.println("Target PID: " + targetPid);
                monitor = new JvmMonitor(targetPid);
            } else {
                System.out.println("Target: current process (PID: " + ManagementFactory.getRuntimeMXBean().getName().split("@")[0] + ")");
                monitor = new JvmMonitor();
            }
            
            ObjectMapper mapper = new ObjectMapper();
            final FileWriter[] writerRef = new FileWriter[1];
            
            if (outputFile != null) {
                writerRef[0] = new FileWriter(outputFile);
                writerRef[0].write("[\n");
            }
            
            monitor.startMonitoring(interval);
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nStopping monitoring...");
                monitor.stopMonitoring();
                if (writerRef[0] != null) {
                    try {
                        writerRef[0].write("\n]");
                        writerRef[0].close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }));
            
            long startTime = System.currentTimeMillis();
            int count = 0;
            
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(interval);
                    
                    // 显示当前指标
                    if (count % 5 == 0) {
                        System.out.println("\n=== JVM Metrics ===");
                        System.out.println("Time: " + new java.util.Date());
                        
                        ObjectNode report = monitor.generateDiagnosticReport();
                        System.out.println("Heap Usage: " + 
                            String.format("%.2f%%", 
                                report.get("metrics").get("heap.usage").asDouble() * 100));
                        System.out.println("Thread Count: " + 
                            report.get("metrics").get("thread.count").asInt());
                        System.out.println("Uptime: " + 
                            formatDuration(report.get("uptime").asLong()));
                        
                        // 检查问题
                        if (report.has("issues")) {
                            System.out.println("\n⚠️  Issues detected:");
                            report.get("issues").fields().forEachRemaining(entry -> {
                                System.out.println("  • " + entry.getValue().asText());
                            });
                        }
                    }
                    
                    // 写入输出文件
                    if (writerRef[0] != null) {
                        if (count > 0) {
                            writerRef[0].write(",\n");
                        }
                        ObjectNode metrics = mapper.createObjectNode();
                        metrics.put("timestamp", System.currentTimeMillis());
                        metrics.set("data", monitor.generateDiagnosticReport());
                        writerRef[0].write(mapper.writeValueAsString(metrics));
                    }
                    
                    count++;
                    
                    // 检查是否达到持续时间
                    if (duration > 0) {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        if (elapsed >= duration) {
                            System.out.println("\nMonitoring duration reached. Stopping...");
                            break;
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            return 0;
        }
        
        private String formatDuration(long millis) {
            long seconds = millis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
            } else if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }
    
    @Command(name = "analyze", 
             description = "Analyze JVM state and generate report")
    static class AnalyzeCommand implements Callable<Integer> {
        
        @Option(names = {"-o", "--output"}, 
                description = "Output file for report (default: report.json)",
                defaultValue = "report.json")
        private File outputFile;
        
        @Option(names = {"-f", "--format"}, 
                description = "Output format: json, html, text (default: json)",
                defaultValue = "json")
        private String format;
        
        @Override
        public Integer call() throws Exception {
            System.out.println("Analyzing JVM state...");
            
            JvmMonitor monitor = new JvmMonitor();
            monitor.collectMetrics(); // 收集一次指标
            
            ObjectNode report = monitor.generateDiagnosticReport();
            ObjectMapper mapper = new ObjectMapper();
            
            // 根据格式输出
            switch (format.toLowerCase()) {
                case "json":
                    String json = mapper.writerWithDefaultPrettyPrinter()
                                       .writeValueAsString(report);
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        writer.write(json);
                    }
                    System.out.println("Report saved to: " + outputFile.getAbsolutePath());
                    break;
                    
                case "text":
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        writer.write("=== JVM Diagnostic Report ===\n");
                        writer.write("Generated: " + new java.util.Date() + "\n");
                        writer.write("JVM: " + report.get("jvmName").asText() + 
                                    " " + report.get("jvmVersion").asText() + "\n");
                        writer.write("Uptime: " + formatDuration(report.get("uptime").asLong()) + "\n\n");
                        
                        writer.write("=== Memory Metrics ===\n");
                        ObjectNode metrics = (ObjectNode) report.get("metrics");
                        writer.write("Heap Usage: " + 
                            String.format("%.2f%%\n", 
                                metrics.get("heap.usage").asDouble() * 100));
                        writer.write("Heap Used: " + 
                            formatBytes(metrics.get("heap.used").asLong()) + "\n");
                        writer.write("Heap Committed: " + 
                            formatBytes(metrics.get("heap.committed").asLong()) + "\n\n");
                        
                        writer.write("=== Thread Metrics ===\n");
                        writer.write("Thread Count: " + metrics.get("thread.count").asInt() + "\n");
                        writer.write("Daemon Threads: " + metrics.get("thread.daemon").asInt() + "\n");
                        writer.write("Peak Threads: " + metrics.get("thread.peak").asInt() + "\n");
                        
                        if (report.has("issues")) {
                            writer.write("\n=== Issues Detected ===\n");
                            report.get("issues").fields().forEachRemaining(entry -> {
                                try {
                                    writer.write("• " + entry.getValue().asText() + "\n");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                        
                        if (report.has("suggestions")) {
                            writer.write("\n=== Suggestions ===\n");
                            report.get("suggestions").fields().forEachRemaining(entry -> {
                                try {
                                    writer.write("• " + entry.getValue().asText() + "\n");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                    System.out.println("Text report saved to: " + outputFile.getAbsolutePath());
                    break;
                    
                case "html":
                    // 简单的 HTML 报告
                    String html = generateHtmlReport(report);
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        writer.write(html);
                    }
                    System.out.println("HTML report saved to: " + outputFile.getAbsolutePath());
                    break;
                    
                default:
                    System.err.println("Unknown format: " + format);
                    return 1;
            }
            
            // 在控制台显示摘要
            System.out.println("\n=== Report Summary ===");
            System.out.println("JVM: " + report.get("jvmName").asText());
            System.out.println("Uptime: " + formatDuration(report.get("uptime").asLong()));
            
            ObjectNode metrics = (ObjectNode) report.get("metrics");
            System.out.println("Heap Usage: " + 
                String.format("%.2f%%", 
                    metrics.get("heap.usage").asDouble() * 100));
            System.out.println("Thread Count: " + metrics.get("thread.count").asInt());
            
            if (report.has("issues")) {
                int issueCount = report.get("issues").size();
                System.out.println("Issues Detected: " + issueCount);
            } else {
                System.out.println("No issues detected");
            }
            
            return 0;
        }
        
        private String formatDuration(long millis) {
            // 简化版本
            long seconds = millis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            if (hours > 0) {
                return String.format("%dh %dm", hours, minutes % 60);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds % 60);
            } else {
                return String.format("%ds", seconds);
            }
        }
        
        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
        
        private String generateHtmlReport(ObjectNode report) {
            return "<!DOCTYPE html>\n" +
                   "<html>\n" +
                   "<head>\n" +
                   "    <title>JVM Diagnostic Report</title>\n" +
                   "    <style>\n" +
                   "        body { font-family: Arial, sans-serif; margin: 40px; }\n" +
                   "        h1 { color: #333; }\n" +
                   "        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }\n" +
                   "        .metric { margin: 5px 0; }\n" +
                   "        .issue { color: #d9534f; background: #f2dede; padding: 10px; margin: 5px 0; border-radius: 3px; }\n" +
                   "        .suggestion { color: #5bc0de; background: #d9edf7; padding: 10px; margin: 5px 0; border-radius: 3px; }\n" +
                   "    </style>\n" +
                   "</head>\n" +
                   "<body>\n" +
                   "    <h1>JVM Diagnostic Report</h1>\n" +
                   "    <div class=\"section\">\n" +
                   "        <h2>System Information</h2>\n" +
                   "        <div class=\"metric\">JVM: " + report.get("jvmName").asText() + "</div>\n" +
                   "        <div class=\"metric\">Version: " + report.get("jvmVersion").asText() + "</div>\n" +
                   "        <div class=\"metric\">Uptime: " + formatDuration(report.get("uptime").asLong()) + "</div>\n" +
                   "        <div class=\"metric\">Report Time: " + new java.util.Date() + "</div>\n" +
                   "    </div>\n" +
                   "    <!-- 其他部分可以通过 JavaScript 动态生成 -->\n" +
                   "    <script>\n" +
                   "        // 这里可以添加动态加载 JSON 数据的代码\n" +
                   "        const reportData = " + report.toString() + ";\n" +
                   "        console.log('Report data loaded:', reportData);\n" +
                   "    </script>\n" +
                   "</body>\n" +
                   "</html>";
        }
    }
    
    @Command(name = "version", 
             description = "Show version information")
    static class VersionCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            System.out.println("JVM Doctor v1.0.0");
            System.out.println("A powerful JVM diagnostic and monitoring tool");
            System.out.println("GitHub: https://github.com/funnyx6/jvm-doctor");
            return 0;
        }
    }
    
    @Override
    public Integer call() throws Exception {
        // 默认显示帮助
        CommandLine.usage(this, System.out);
        return 0;
    }
    
    public static void main(String[] args) {
        JvmDoctorCli cli = new JvmDoctorCli();
        CommandLine commandLine = new CommandLine(cli);
        
        // 注册子命令
        commandLine.addSubcommand("monitor", new MonitorCommand());
        commandLine.addSubcommand("analyze", new AnalyzeCommand());
        commandLine.addSubcommand("version", new VersionCommand());
        
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}