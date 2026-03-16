# 通用代码执行模块 (dailw-judge)

该模块是一个高性能、安全且可扩展的在线评测引擎，旨在执行多种编程语言的代码。它利用 Docker 进行沙箱隔离，并使用 Java 21 虚拟线程实现高并发处理。

## 特性

- **多语言支持**：支持 C、C++、Java、Python、Go 等。可通过 `src/main/resources/languages.yaml` 进行配置。
- **沙箱执行**：使用 Docker 容器隔离执行环境，确保安全性。
- **容器池化**：实现了一个健壮的 Docker 容器池（基于 Apache Commons Pool2）以复用容器，显著降低启动延迟。
- **并发执行**：利用 Java `CompletableFuture` 和自定义线程池，支持对单个提交的多个测试用例进行并发执行。
- **多测试用例支持**：能够为每个提交运行多个输入/输出测试用例。
- **资源限制**：可针对每种语言或提交配置 CPU、内存和时间限制。
- **可观测性**：集成了 Micrometer 和 Prometheus 以进行实时指标监控。

## 架构

- **核心服务**：Spring Boot 3.2 应用程序。
- **沙箱**：基于 Docker 的执行环境。
- **并发机制**：
    - **虚拟线程**：启用以实现高吞吐量 I/O。
    - **容器池**：复用“热”容器以避免冷启动开销。
    - **异步评测**：并行执行测试用例。
- **配置管理**：通过 `application.yaml` 和 `JudgeProperties` 进行集中配置。

## 前置要求

- **JDK 17+**（开发时考虑了 JDK 21 特性，兼容 JDK 17）
- **Docker Desktop** (Windows) 或 **Docker Engine** (Linux)
    - *注意*：确保 Docker 守护进程已暴露或针对 `docker-java` 进行了正确配置。
- **Maven** 3.8+

## 配置

### 应用设置
编辑 `src/main/resources/application.yaml` 以配置：
- **服务器端口**：默认为 `9119`。
- **评测设置**：
    - 池大小 (`judge.pool.max-total`)
    - 线程池大小 (`judge.thread.core-pool-size`)
    - 沙箱限制 (`judge.sandbox.memory-limit`)
    - 工作区路径 (`judge.workspace.root`)

### 语言支持
编辑 `src/main/resources/languages.yaml` 以添加或修改支持的语言、Docker 镜像以及编译/运行命令。

## 运行

1. **启动 Docker**：确保你的 Docker 环境正在运行。
2. **构建并运行**：
   ```bash
   mvn spring-boot:run
   ```
   服务将在端口 `9119` 上启动。

## API 使用

### 提交代码
**POST** `/judge`

提交包含多个测试用例的解答。

**请求体**：
```json
{
  "id": "submission-uuid-123",
  "language": "python",
  "sourceCode": "import sys\nfor line in sys.stdin:\n    print(line.strip())",
  "testCases": [
    {
      "input": "hello",
      "expectedOutput": "hello"
    },
    {
      "input": "world",
      "expectedOutput": "world"
    }
  ],
  "timeLimit": 1000,
  "memoryLimit": 128000000
}
```

**响应**：
```json
{
  "submissionId": "submission-uuid-123",
  "status": "ACCEPTED",
  "timeUsed": 150,
  "memoryUsed": 10485760,
  "message": null,
  "testCaseResults": [
    {
      "testCaseId": 1,
      "status": "ACCEPTED",
      "timeUsed": 120,
      "memoryUsed": 10485760,
      "output": "hello\n",
      "message": ""
    },
    {
      "testCaseId": 2,
      "status": "ACCEPTED",
      "timeUsed": 150,
      "memoryUsed": 10240000,
      "output": "world\n",
      "message": ""
    }
  ]
}
```

## 指标

Prometheus 指标暴露在 `http://localhost:9119/actuator/prometheus`。

关键指标：
- `judge_submissions_total`: 处理的提交总数计数器。
- `judge_duration_seconds`: 处理时间的直方图。

## 项目结构

- `com.judge.core`: 领域模型 (Submission, JudgeResult, TestCase)。
- `com.judge.service`: 业务逻辑 (JudgeService, CompilerService)。
- `com.judge.sandbox`: Docker 沙箱实现及池化逻辑。
- `com.judge.manager`: 文件系统和工作区管理。
- `com.judge.config`: 应用程序配置。
- `com.judge.exception`: 自定义异常处理。
