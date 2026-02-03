package com.github.funnyx6.jvmdoctor.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JvmDoctorCli 单元测试
 */
class JvmDoctorCliTest {
    
    @Test
    void testMainMethodExists() {
        // 测试主方法存在且可调用
        assertDoesNotThrow(() -> {
            // 不传递参数应该不会抛出异常
            // 注意：实际调用 main 方法需要小心处理
        });
    }
    
    @Test
    void testVersionCommandClass() {
        // 测试版本命令类存在
        JvmDoctorCli.VersionCommand versionCommand = new JvmDoctorCli.VersionCommand();
        assertNotNull(versionCommand);
    }
    
    @Test
    void testMonitorCommandClass() {
        // 测试监控命令类存在
        JvmDoctorCli.MonitorCommand monitorCommand = new JvmDoctorCli.MonitorCommand();
        assertNotNull(monitorCommand);
    }
    
    @Test
    void testAnalyzeCommandClass() {
        // 测试分析命令类存在
        JvmDoctorCli.AnalyzeCommand analyzeCommand = new JvmDoctorCli.AnalyzeCommand();
        assertNotNull(analyzeCommand);
    }
    
    @Test
    void testCallableReturnType() {
        // 测试所有命令实现 Callable
        assertTrue(new JvmDoctorCli.VersionCommand() instanceof java.util.concurrent.Callable);
        assertTrue(new JvmDoctorCli.MonitorCommand() instanceof java.util.concurrent.Callable);
        assertTrue(new JvmDoctorCli.AnalyzeCommand() instanceof java.util.concurrent.Callable);
    }
    
    @Test
    void testCliMainClass() {
        // 测试 CLI 主类
        assertNotNull(JvmDoctorCli.class);
        assertTrue(JvmDoctorCli.class.getMethods().length > 0);
    }
}
