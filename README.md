# JVM Doctor

一个强大的 JVM 诊断和监控平台，支持本地诊断和分布式监控两种模式。

## 功能特性

### 本地诊断模式（CLI）
- 🔍 **实时监控**：监控 JVM 内存、GC、线程、CPU 使用情况
- 📊 **性能分析**：分析热点方法、内存泄漏、锁竞争
- 🚨 **异常检测**：自动检测 OOM、死锁、CPU 飙高等问题
- 📈 **可视化报告**：生成 HTML/JSON/TEXT 格式的诊断报告

### 分布式监控模式（Server + Agent）
- 🌐 **应用注册**：自动注册目标 JVM 应用
- 📡 **实时采集**：定时采集并上报 JVM 指标
- 📊 **可视化仪表盘**：Vue.js 构建的实时监控界面
- 🚨 **告警系统**：堆内存、CPU、GC 等阈值告警
- 🔄 **WebSocket 实时推送**：指标和告警实时更新

## 架构

```
┌─────────────────────────────────────────────────────────────┐
│                      JVM Doctor Server                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Spring Boot + SQLite + WebSocket                     │  │
│  │  - 应用注册/心跳                                       │  │
│  │  - 指标接收存储                                        │  │
│  │  - 告警检测                                            │  │
│  │  - REST API + WebSocket                               │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Vue.js Dashboard                                     │  │
│  │  - 应用列表                                            │  │
│  │  - 实时图表                                            │  │
│  │  - 告警中心                                            │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          ↑ HTTP/WebSocket
┌─────────────────────────────────────────────────────────────┐
│                      Target Applications                     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  java -javaagent:jvm-doctor-agent.jar -jar app.jar    │  │
│  │  - 自动注册                                            │  │
│  │  - 指标上报                                            │  │
│  │  - 心跳保活                                            │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 前提条件
- Java 8+
- Maven 3.6+

### 构建项目
```bash
git clone https://gitee.com/darwin_west/jvm-doctor.git
cd jvm-doctor
mvn clean package -DskipTests
```

---

## 模式一：本地诊断（CLI）

### 命令列表
```bash
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar --help
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor --help
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar analyze --help
```

### 监控命令
```bash
# 监控当前 JVM 进程（每5秒刷新）
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor

# 指定刷新间隔（3秒）
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor -i 3

# 指定监控时长（60秒后自动停止）
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor -d 60

# 输出到文件（JSON格式）
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor -o metrics.json

# 组合使用：3秒间隔、输出到文件
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor -i 3 -o metrics.json
```

### 监控其他 JVM 进程
```bash
# 监控指定 PID 的进程（需要 tools.jar）
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor -p 12345

# 完整命令：PID + 间隔 + 输出
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar monitor -p 12345 -i 3 -o remote-metrics.json
```

> **注意**：监控其他进程需要目标 JVM 开启 JMX：
> ```bash
> java -Dcom.sun.management.jmxremote \
>      -Dcom.sun.management.jmxremote.port=9010 \
>      -Dcom.sun.management.jmxremote.ssl=false \
>      -Dcom.sun.management.jmxremote.authenticate=false \
>      -jar your-app.jar
> ```

### 分析命令
```bash
# 生成 JSON 报告
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar analyze

# 生成 HTML 报告
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar analyze -f html -o report.html

# 生成 TEXT 报告
java -jar jvm-doctor-cli/target/jvm-doctor-cli-1.0.0-jar-with-dependencies.jar analyze -f text -o report.txt
```

---

## 模式二：分布式监控（Server + Agent）

### 1. 启动 Server

```bash
# 启动 Web 服务（默认端口 8080）
java -jar jvm-doctor-web/target/jvm-doctor-web-1.0.0.jar

# 或指定端口
java -jar jvm-doctor-web/target/jvm-doctor-web-1.0.0.jar --server.port=9000
```

访问控制台：http://localhost:8080

### 2. 配置目标应用

**方式一：启动参数（推荐）**
```bash
java -javaagent:jvm-doctor-agent.jar -jar your-app.jar
```

**方式二：自定义 Server 地址**
```bash
java -javaagent:jvm-doctor-agent.jar=server.url=http://localhost:8080 -jar your-app.jar
```

**方式三：系统属性**
```bash
java -Djvm-doctor.server.url=http://localhost:8080 \
     -Djvm-doctor.report.interval=10 \
     -javaagent:jvm-doctor-agent.jar \
     -jar your-app.jar
```

**方式四：配置文件**
```bash
# 创建 jvm-doctor-agent.properties
echo "server.url=http://localhost:8080" > jvm-doctor-agent.properties
echo "report.interval=10" >> jvm-doctor-agent.properties

java -javaagent:jvm-doctor-agent.jar -jar your-app.jar
```

### 3. 配置参数说明

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `server.url` | Server 地址 | `http://localhost:8080` |
| `report.interval` | 上报间隔（秒） | `30` |
| `app.name` | 应用名称 | 自动检测 |
| `app.host` | 主机地址 | 自动检测 |

### 4. 动态挂载（可选）

如果无法重启应用，可以使用 attach 方式动态挂载：

**方式一：列出所有 Java 进程**
```bash
java -jar jvm-doctor-agent/target/jvm-doctor-agent-attach.jar --list
```

**方式二：附加到指定进程**
```bash
java -jar jvm-doctor-agent/target/jvm-doctor-agent-attach.jar --pid 12345

# 或指定 Server 地址和上报间隔
java -jar jvm-doctor-agent/target/jvm-doctor-agent-attach.jar --pid 12345 --url http://localhost:8080 --interval 10
```

> 注：动态挂载需要目标 JVM 开启 `-Djdk.attach.allowAttachSelf=true`

---

## API 文档

### 应用注册

**注册应用**
```http
POST /api/apps/register
Content-Type: application/json

{
  "appName": "my-service",
  "host": "192.168.1.100",
  "port": 8080,
  "jvmName": "OpenJDK 1.8.0_392",
  "jvmVersion": "1.8.0_392",
  "startTime": 1738588800000
}
```

**响应**
```json
{
  "appId": 1,
  "status": "running",
  "message": "App registered successfully",
  "serverTime": 1738588800000
}
```

**心跳**
```http
POST /api/apps/{appId}/heartbeat
```

**下线**
```http
POST /api/apps/{appId}/offline
```

### 指标

**上报指标**
```http
POST /api/metrics
Content-Type: application/json

{
  "appId": 1,
  "metrics": {
    "heap.used": 123456789,
    "heap.max": 536870912,
    "heap.usage": 0.23,
    "gc.count": 150,
    "gc.time": 5000,
    "thread.count": 42,
    "cpu.usage": 0.25,
    "uptime": 3600000
  }
}
```

**获取最新指标**
```http
GET /api/metrics/{appId}/latest
```

**获取指标历史**
```http
GET /api/metrics/{appId}/history?since=timestamp
```

### 告警

**获取告警列表**
```http
GET /api/alerts
GET /api/alerts/unacknowledged
```

**确认告警**
```http
POST /api/alerts/{alertId}/acknowledge
Content-Type: application/json

{
  "acknowledgedBy": "admin"
}
```

### 健康检查
```http
GET /api/health
GET /api/info
```

### WebSocket

**连接**
```bash
ws://localhost:8080/ws/metrics
```

**订阅特定应用**
```bash
ws://localhost:8080/ws/metrics?appId=1
```

**消息格式**

指标推送：
```json
{
  "type": "metrics",
  "appId": 1,
  "timestamp": 1738588800000,
  "heapUsed": 123456789,
  "heapMax": 536870912,
  "heapUsage": 0.23,
  "threadCount": 42,
  "cpuUsage": 0.25
}
```

告警推送：
```json
{
  "type": "alert",
  "alertId": 1,
  "appId": 1,
  "alertType": "high_heap_usage",
  "alertMsg": "Heap usage: 92.5%",
  "alertLevel": "warning",
  "createdAt": 1738588800000
}
```

---

## 项目结构

```
jvm-doctor/
├── jvm-doctor-core/           # 核心模块（JVM 指标采集）
├── jvm-doctor-agent/          # Java Agent（部署到目标应用）
├── jvm-doctor-cli/            # 命令行工具（本地诊断）
├── jvm-doctor-web/            # Web 服务 + Dashboard
└── pom.xml                    # 父 POM
```

---

## 技术栈

- **Java 8** - 开发语言
- **Maven** - 构建工具
- **Spring Boot 2.5.4** - Web 框架
- **SQLite** - 轻量数据库
- **Vue.js 3** - 前端框架
- **Chart.js** - 图表库
- **Picocli 4.7.5** - CLI 框架

---

## 贡献指南

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License
