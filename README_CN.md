# W-judge — 通用在线判题执行引擎

W-judge 是一个**高性能、安全、可扩展的在线判题（Online Judge）执行引擎微服务**，用于接收代码提交，在隔离的 Docker 容器中编译并执行，运行测试用例并返回评判结果。它专为在线编程竞赛平台（OJ 系统，如 LeetCode、Codeforces）的核心判题场景而设计。

---

## 目录

- [特性](#特性)
- [系统架构](#系统架构)
- [中间件分析](#中间件分析)
- [快速开始](#快速开始)
- [API 参考](#api-参考)
- [配置说明](#配置说明)
- [语言配置](#语言配置)
- [监控和指标](#监控和指标)
- [项目结构](#项目结构)
- [测试提交示例](#测试提交示例)
- [开发与测试](#开发与测试)
- [未来规划](#未来规划)

---

## 特性

- **多语言支持** — 支持 C、C++、Java、Python、Go，所有语言配置通过 YAML 管理，易于扩展。
- **Docker 沙箱隔离** — 每个提交在独立的 Docker 容器中执行，无网络访问、严格限制 CPU/内存，执行完毕自动清理。
- **容器池化** — 基于 Apache Commons Pool2 实现容器池，复用"热"容器，避免 `docker run` 冷启动开销（约 2-5 秒/容器）。
- **并行测试用例执行** — 基于 Java 21 虚拟线程 + `CompletableFuture` 实现测试用例级并发，大幅降低总体判题延迟。
- **资源强制限制** — 每个提交和每种语言均可独立配置 CPU、内存和时间限制。
- **全方位可观测性** — 集成 Micrometer + Prometheus，提供提交计数、执行时间直方图和健康检查。
- **无状态可水平扩展** — 无需数据库，可在负载均衡后水平扩展。

---

## 系统架构

### 执行流程

```
┌──────────────┐     POST /judge      ┌──────────────────────┐
│   客户端      │ ──────────────────▶  │   JudgeController    │
│ (OJ 前端)    │                     │   (REST API 层)       │
└──────────────┘                     └──────────────────────┘
                                              │
                                              ▼
                                     ┌──────────────────────┐
                                     │    JudgeService       │
                                     │   (核心编排层)         │
                                     └──────────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
                    ▼                         ▼                         ▼
         ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────────┐
         │ 语言配置加载器    │     │  CompilerService │     │  WorkspaceManager     │
         │  (languages.yaml)│     │ (Docker 沙箱编译) │     │  (工作区文件管理)      │
         └──────────────────┘     └──────────────────┘     └──────────────────────┘
                                        │                          │
                                        ▼                          ▼
                              ┌──────────────────┐     ┌──────────────────────┐
                              │ PooledDocker     │     │  /tmp/W-judge/       │
                              │   Sandbox        │     │  workspace/{id}/     │
                              │ (池化沙箱执行)    │     │  (临时工作目录)       │
                              └──────────────────┘     └──────────────────────┘
                                        │
                              ┌─────────┼─────────┐
                              │         │         │
                              ▼         ▼         ▼
                        ┌───────────────────────────┐
                        │  测试用例 1   测试用例 2    │  ...  测试用例 N
                        │  (虚拟线程 + CompletableFuture 并行执行)
                        └───────────────────────────┘
                                        │
                                        ▼
                              ┌──────────────────┐
                              │   JudgeResult     │
                              │  (聚合结果)       │
                              └──────────────────┘
```

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot 应用                          │
│                                                             │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ Controller │  │   Service   │  │    Sandbox       │    │
│  │  层        │─▶│   层        │─▶│    层           │    │
│  └────────────┘  └──────────────┘  └───────┬──────────┘    │
│                                            │               │
│  ┌────────────┐  ┌──────────────┐  ┌───────▼──────────┐    │
│  │  Config   │  │   Manager   │  │  Docker 容器池    │    │
│  │  层       │  │ (工作区管理) │  │  (Commons Pool2) │    │
│  └────────────┘  └──────────────┘  └──────────────────┘    │
│                                                             │
│  ┌──────────────────────────────────────────────────┐      │
│  │  Micrometer + Prometheus 指标注册中心             │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 关键设计决策

| 设计点 | 实现方式 | 原因 |
|---|---|---|
| **容器池化** | `GenericKeyedObjectPool<String, String>` (Apache Commons Pool2) | "热"容器将执行延迟降低 2-5 秒，按 Docker 镜像名分池 |
| **虚拟线程** | Java 21 VT + `SimpleAsyncTaskExecutor` | 数百个并发测试用例仅需极少的 OS 线程开销 |
| **输出比对** | 去除首尾空白后的字符串等值比较 | 简单确定，适合严格的 I/O 匹配 |
| **无数据库** | 无状态 REST 服务 | 简化部署、水平扩展和容器编排 |
| **双沙箱实现** | `DockerSandbox` (新容器) + `PooledDockerSandbox` (池化, `@Primary`) | 默认池化模式，特殊情况可回退 |

---

## 中间件分析

W-judge 集成了以下中间件和基础设施组件：

### 1. Docker Engine & docker-java

**作用**：核心执行沙箱。每次编译和测试用例执行都在 Docker 容器内完成。

**使用方式**：
- `DockerSandbox` — 每次执行创建一个新容器（创建 → 启动 → 等待 → 收集 → 删除）。
- `PooledDockerSandbox` — 从池中借用预热容器，通过 `docker exec` 执行，执行完毕归还。
- `DockerContainerFactory` — 以 `tail -f /dev/null` 为保持存活命令创建容器，配置无网络、内存限制和 CPU 限制。

**配置**：
- Docker 主机自动检测：Windows 命名管道 `npipe:////./pipe/docker_engine` 或 Unix 套接字 `unix:///var/run/docker.sock`。
- 可通过 `judge.docker.host` 自定义地址。
- HTTP 传输：`ZerodepDockerHttpClient`，最大 100 连接，30s 连接超时，45s 响应超时。

**版本**：docker-java 3.3.6（core + zerodep transport）

### 2. Apache Commons Pool2

**作用**：Docker 容器池化，最小化冷启动延迟。

**使用方式**：
- `GenericKeyedObjectPool<String, String>` — 按 Docker 镜像名分池（每种语言一个子池）。
- `BaseKeyedPooledObjectFactory` — 创建、验证和销毁容器。
- 池配置：maxTotal=50, maxTotalPerKey=10, maxIdlePerKey=5, maxWait=10s, testOnBorrow=true。
- 超时时：容器被标记为失效（销毁）而非归还池中。

**为什么选择 Pool2？**：轻量、成熟、线程安全、原生支持按键分池。

### 3. Spring Boot 3.5.0

**作用**：应用框架，提供依赖注入、REST API、配置管理和健康检查端点。

**模块**：
- `spring-boot-starter-web` — 嵌入式 Tomcat、REST 控制器 (`JudgeController`)。
- `spring-boot-starter-actuator` — 健康检查、Prometheus 端点。
- `spring-boot-starter-test` — 集成测试框架。

### 4. Micrometer + Prometheus

**作用**：指标采集和暴露，用于监控和可观测性。

**指标**：

| 指标 | 类型 | 标签 | 描述 |
|---|---|---|---|
| `judge.submissions.total` | Counter | `language`, `status` | 处理的提交总数 |
| `judge.duration` | Timer | `language` | 处理时间直方图 |

**暴露地址**：`GET /actuator/prometheus`

### 5. Java 21 虚拟线程 (Virtual Threads)

**作用**：轻量级并发，用于并行测试用例执行。

**使用方式**：全局启用 `spring.threads.virtual.enabled=true`。`judgeExecutor` Bean 使用启用了虚拟线程的 `SimpleAsyncTaskExecutor`。每个测试用例通过 `CompletableFuture.supplyAsync()` 在此执行器上运行。

### 6. SnakeYAML（通过 Spring Boot 引入）

**作用**：在启动时将 `languages.yaml` 解析为 `LanguageConfig` 对象。

### 7. Lombok

**作用**：通过 `@Slf4j`、`@RequiredArgsConstructor`、`@Builder`、`@Data` 等注解减少样板代码。

---

## 快速开始

### 环境要求

- **JDK 17+**（推荐 JDK 21）
- **Docker**（Windows/Mac 使用 Docker Desktop，Linux 使用 Docker Engine）
- **Maven** 3.8+（或使用项目自带的 Maven Wrapper）

### 启动服务

```bash
# 1. 克隆并进入项目
git clone <repo-url>
cd W-judge

# 2. 确保 Docker 正在运行

# 3. 构建并启动
./mvnw spring-boot:run
```

服务默认在 **端口 9119** 启动。验证：

```bash
curl http://localhost:9119/actuator/health
```

### 测试提交

```bash
curl -X POST http://localhost:9119/judge \
  -H "Content-Type: application/json" \
  -d '{
    "id": "hello-py",
    "language": "python",
    "sourceCode": "print(input())",
    "testCases": [
      { "input": "hello", "expectedOutput": "hello" }
    ],
    "timeLimit": 1000,
    "memoryLimit": 268435456
  }'
```

---

## API 参考

### POST /judge — 提交代码判题

提交源代码和测试用例，进行编译和执行。

#### 请求体

```json
{
  "id": "string (必填) - 唯一提交标识",
  "language": "string (必填) - 语言名称 (c, cpp, java, python, go)",
  "sourceCode": "string (必填) - 待编译执行的源代码",
  "testCases": [
    {
      "input": "string (可选) - 该测试用例的标准输入",
      "expectedOutput": "string (可选) - 预期标准输出"
    }
  ],
  "timeLimit": "integer (可选) - 时间限制，单位毫秒",
  "memoryLimit": "integer (可选) - 内存限制，单位字节"
}
```

#### 响应体

```json
{
  "submissionId": "string - 回显请求ID",
  "status": "string - 最终评判结果",
  "timeUsed": "integer - 所有测试用例中最长耗时 (ms)",
  "memoryUsed": "integer - 所有测试用例中最大内存 (bytes)",
  "message": "string or null - 错误信息",
  "testCaseResults": [
    {
      "testCaseId": "integer - 从 1 开始的序号",
      "status": "string - 单个测试用例结果",
      "timeUsed": "integer",
      "memoryUsed": "integer",
      "output": "string - 实际标准输出",
      "message": "string - 标准错误或错误详情"
    }
  ]
}
```

#### 状态码说明

| 状态 | 含义 |
|---|---|
| `ACCEPTED` | 输出与预期输出一致 |
| `WRONG_ANSWER` | 输出与预期输出不一致 |
| `TIME_LIMIT_EXCEEDED` | 执行超时（TLE） |
| `MEMORY_LIMIT_EXCEEDED` | 超出内存限制（MLE） |
| `RUNTIME_ERROR` | 非零退出码（RE） |
| `COMPILATION_ERROR` | 编译失败（CE） |
| `SYSTEM_ERROR` | 系统内部错误 |
| `PENDING` | 等待处理 |
| `JUDGING` | 正在评测中 |

---

## 配置说明

### application.yaml 完整配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 9119 | HTTP 服务端口 |
| `spring.threads.virtual.enabled` | true | 启用 Java 21 虚拟线程 |
| `judge.pool.max-total` | 50 | 容器池最大总数 |
| `judge.pool.max-total-per-key` | 10 | 每种语言最大容器数 |
| `judge.pool.max-idle-per-key` | 5 | 每种语言最大空闲数 |
| `judge.pool.max-wait` | 10s | 借容器最大等待时间 |
| `judge.sandbox.memory-limit` | 1GB | 每个容器默认内存限制 |
| `judge.sandbox.nano-cpus` | 1 CPU | 每个容器默认 CPU 限制 |
| `judge.compiler.time-limit` | 10000ms | 编译超时时间 |
| `judge.compiler.memory-limit` | 512MB | 编译内存限制 |
| `judge.workspace.root` | /tmp/W-judge/workspace | 源代码工作目录 |
| `judge.docker.host` | 自动检测 | Docker 守护进程 URI |

---

## 语言配置

编辑 `src/main/resources/languages.yaml` 文件进行语言配置：

```yaml
languages:
  - name: "python"
    imageName: "python:3.12-slim"
    sourceExtension: ".py"
    srcFileName: "main.py"
    compileCmd: "python3 -B -m py_compile main.py"
    runCmd: "python3 -B main.py"
    maxCpuTime: 5000
    maxMemory: 268435456
```

预置支持的语言：

| 语言 | Docker 镜像 | 编译命令 | 运行命令 |
|---|---|---|---|
| C | gcc:13.2 | `gcc main.c -o main -O2 -Wall -lm -static -std=c11` | `./main` |
| C++ | gcc:13.2 | `g++ main.cpp -o main -O2 -Wall -lm -static -std=c++20` | `./main` |
| Java | eclipse-temurin:8 | `javac Main.java` | `java -Xmx256m -Xss1m Main` |
| Python | python:3.12-slim | `python3 -B -m py_compile main.py`（语法检查） | `python3 -B main.py` |
| Go | golang:1.26.1 | `go build -o main main.go` | `./main` |

添加新语言只需在 `languages.yaml` 中添加条目，并确保对应的 Docker 镜像可用。

---

## 监控和指标

Prometheus 指标地址：`http://localhost:9119/actuator/prometheus`

### 关键指标

```prometheus
# 按语言和状态的提交计数器
judge_submissions_total{language="python",status="ACCEPTED"} 42

# 处理时间直方图（秒）
judge_duration_seconds_count{language="python"} 42
judge_duration_seconds_sum{language="python"} 12.5
```

### 健康检查

```bash
curl http://localhost:9119/actuator/health
```

查看所有 Actuator 端点：

```bash
curl http://localhost:9119/actuator
```

---

## 项目结构

```
src/main/java/com/judge/
├── DailwJudgeApplication.java         # Spring Boot 入口
├── config/
│   ├── ContainerPoolConfig.java       # Commons Pool2 容器池配置
│   ├── DockerConfig.java              # DockerClient Bean 配置
│   ├── JudgeProperties.java           # 配置属性映射
│   ├── LanguageConfigLoader.java      # YAML 语言配置加载器
│   └── ThreadPoolConfig.java          # 虚拟线程执行器配置
├── controller/
│   └── JudgeController.java           # POST /judge REST 端点
├── core/
│   ├── JudgeResult.java               # 聚合判题结果
│   ├── JudgeStatus.java               # 枚举：9 种评判状态
│   ├── LanguageConfig.java            # 语言配置模型
│   ├── Submission.java                # 提交请求模型
│   ├── TestCase.java                  # 测试用例定义
│   └── TestCaseResult.java            # 单用例结果
├── exception/
│   ├── CompilationException.java      # 编译异常
│   ├── JudgeException.java            # 判题异常（基类）
│   └── SandboxException.java          # 沙箱异常
├── manager/
│   └── WorkspaceManager.java          # 临时工作区生命周期管理
├── sandbox/
│   ├── DockerSandbox.java             # 新容器沙箱实现
│   ├── DockerStatsCollector.java      # 实时内存监控
│   ├── PooledDockerSandbox.java       # 池化容器沙箱（默认）
│   ├── Sandbox.java                   # 沙箱接口
│   ├── SandboxRequest.java            # 沙箱执行请求
│   ├── SandboxResult.java             # 沙箱执行结果
│   └── pool/
│       └── DockerContainerFactory.java # Pool2 对象工厂
└── service/
    ├── CompileResult.java             # 编译结果
    ├── CompilerService.java           # 编译逻辑
    └── JudgeService.java              # 核心判题编排
```

---

## 开发与测试

### 运行测试

```bash
./mvnw test
```

集成测试套件（`JudgeIntegrationTest`）包含：

| 测试用例 | 说明 |
|---|---|
| `testPythonHelloWorld` | 基础验证：正确输出 → ACCEPTED |
| `testTimeLimitExceeded` | 死循环检测 → TLE |
| `testMultipleTestCases` | 多测试用例全部正确 |
| `testMultipleTestCasesWithOneFailure` | 部分失败 → WRONG_ANSWER |
| `testConcurrentPerformance` | 验证并行执行（5 个 1 秒用例 4 秒内完成） |

### 打包构建

```bash
./mvnw clean package -DskipTests
java -jar target/judge-0.0.1-SNAPSHOT.jar
```

---

## 未来规划

- [ ] **Special Judge（特判）** — 支持自定义比较程序，适用于非严格输出匹配的场景
- [ ] **交互式题目支持** — 评测系统与用户程序实时交互
- [ ] **提交队列与限流** — 防止高并发下系统过载
- [ ] **WebSocket 实时推送** — 判题过程中实时推送进度
- [ ] **认证与授权** — API Key 或 JWT 访问控制
- [ ] **结果缓存** — 缓存相同提交，跳过重复执行
- [ ] **Kubernetes 原生调度** — 在 K8s 集群上动态调度容器
- [ ] **可插拔沙箱后端** — 支持 gVisor、Firecracker 等更多沙箱实现
