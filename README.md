# JVM Doctor

一个强大的 JVM 诊断和监控工具，用于分析和优化 Java 应用程序性能。

## 功能特性

- 🔍 **实时监控**：监控 JVM 内存、GC、线程、CPU 使用情况
- 📊 **性能分析**：分析热点方法、内存泄漏、锁竞争
- 🚨 **异常检测**：自动检测 OOM、死锁、CPU 飙高等问题
- 📈 **可视化报告**：生成 HTML/PDF 格式的诊断报告
- 🔧 **调优建议**：基于分析结果提供 JVM 参数调优建议
- 🐳 **容器支持**：支持 Docker/Kubernetes 环境下的 JVM 诊断

## 快速开始

### 前提条件
- Java 8 或更高版本
- Maven 3.6+

### 安装
```bash
# 克隆项目
git clone https://github.com/funnyx6/jvm-doctor.git
cd jvm-doctor

# 构建项目
mvn clean package

# 运行
java -jar target/jvm-doctor-1.0.0.jar
```

### 使用示例
```bash
# 监控本地 Java 进程
jvm-doctor monitor --pid 12345

# 分析堆转储文件
jvm-doctor analyze --heap-dump heapdump.hprof

# 生成诊断报告
jvm-doctor report --output report.html
```

## 项目结构
```
jvm-doctor/
├── jvm-doctor-core/     # 核心模块
├── jvm-doctor-agent/    # Java Agent 模块
├── jvm-doctor-cli/      # 命令行接口
├── jvm-doctor-web/      # Web 管理界面
└── jvm-doctor-docs/     # 文档
```

## 技术栈
- **Java 8** - 主开发语言
- **Maven** - 构建工具
- **Spring Boot 2.5.4** - Web 框架
- **JVM TI** - JVM 工具接口
- **Micrometer 1.7.10** - 指标收集
- **Picocli 4.7.5** - 命令行解析
- **Jackson 2.12.5** - JSON 处理

## 贡献指南
欢迎提交 Issue 和 Pull Request！

## 许可证
MIT License