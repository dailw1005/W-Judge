# W-judge — Universal Online Judge Execution Engine

W-judge is a **high-performance, secure, and extensible online judge engine** that accepts code submissions, compiles and executes them inside isolated Docker containers, runs test cases, and returns verdicts. It is designed as the core judging microservice for online programming contest platforms (OJ systems like LeetCode or Codeforces).

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Middleware Analysis](#middleware-analysis)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Language Configuration](#language-configuration)
- [Monitoring & Metrics](#monitoring--metrics)
- [Project Structure](#project-structure)
- [Test Submissions](#test-submissions)
- [Development & Testing](#development--testing)
- [Roadmap](#roadmap)

---

## Features

- **Multi-Language Support** — C, C++, Java, Python, Go. Fully configurable via YAML.
- **Docker Sandboxed Execution** — Each submission runs in an isolated container with no network access, strict CPU/memory limits, and automatic cleanup.
- **Container Pooling** — Apache Commons Pool2-based container pool reuses "warm" containers, eliminating the cold-start overhead of `docker run` (~2-5s per container).
- **Parallel Test Case Execution** — Java 21 Virtual Threads + `CompletableFuture` execute test cases concurrently for minimal latency.
- **Resource Enforcement** — Configurable per-submission and per-language CPU, memory, and time limits.
- **Full Observability** — Micrometer + Prometheus integration for submission counters, execution histograms, and health checks.
- **Stateless & Scalable** — No database dependency; can be horizontally scaled behind a load balancer.

---

## Architecture

### High-Level Flow

```
┌──────────────┐     POST /judge      ┌──────────────────────┐
│   Client     │ ──────────────────▶  │   JudgeController    │
│ (OJ Frontend)│                     │   (REST API Layer)    │
└──────────────┘                     └──────────────────────┘
                                              │
                                              ▼
                                     ┌──────────────────────┐
                                     │    JudgeService       │
                                     │   (Orchestration)     │
                                     └──────────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
                    ▼                         ▼                         ▼
         ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────────┐
         │ LanguageConfig   │     │ CompilerService  │     │  WorkspaceManager     │
         │   Loader         │     │ (Docker Sandbox) │     │  (File I/O)           │
         └──────────────────┘     └──────────────────┘     └──────────────────────┘
                                        │                          │
                                        ▼                          ▼
                              ┌──────────────────┐     ┌──────────────────────┐
                              │ PooledDocker     │     │  /tmp/W-judge/       │
                              │   Sandbox        │     │  workspace/{id}/     │
                              └──────────────────┘     └──────────────────────┘
                                        │
                              ┌─────────┼─────────┐
                              │         │         │
                              ▼         ▼         ▼
                        ┌───────────────────────────┐
                        │  Test Case 1   Test Case 2 │  ...  Test Case N
                        │  (Virtual Thread + CompletableFuture)
                        └───────────────────────────┘
                                        │
                                        ▼
                              ┌──────────────────┐
                              │   JudgeResult     │
                              │  (Aggregated)     │
                              └──────────────────┘
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                   │
│                                                             │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐    │
│  │ Controller │  │   Service    │  │    Sandbox        │    │
│  │  Layer     │─▶│   Layer      │─▶│    Layer          │    │
│  └────────────┘  └──────────────┘  └───────┬──────────┘    │
│                                            │               │
│  ┌────────────┐  ┌──────────────┐  ┌───────▼──────────┐    │
│  │  Config    │  │   Manager    │  │  DockerContainer  │    │
│  │  Layer     │  │ (Workspace)  │  │  Pool (CP2)       │    │
│  └────────────┘  └──────────────┘  └──────────────────┘    │
│                                                             │
│  ┌──────────────────────────────────────────────────┐      │
│  │  Micrometer + Prometheus Registry               │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Implementation | Rationale |
|---|---|---|
| **Container Pooling** | `GenericKeyedObjectPool<String, String>` (Apache Commons Pool2) | "Warm" containers reduce execution latency by 2-5s per run. Pool is keyed by Docker image name. |
| **Virtual Threads** | Java 21 VT + `SimpleAsyncTaskExecutor` | Allows hundreds of concurrent test cases with minimal OS thread overhead. |
| **Output Comparison** | Whitespace-trimmed string equality | Simple and deterministic; suitable for strict I/O matching. |
| **No Database** | Stateless REST service | Simplifies deployment, horizontal scaling, and container orchestration. |
| **Two Sandbox Implementations** | `DockerSandbox` (fresh container) + `PooledDockerSandbox` (pooled, `@Primary`) | Pooled is default; fallback available for special cases. |

---

## Middleware Analysis

W-judge integrates the following middleware and infrastructure components:

### 1. Docker Engine & docker-java

**Role**: Core execution sandbox. Every compilation and test case execution runs inside a Docker container.

**Usage**:
- `DockerSandbox` — creates a new container per execution (create → start → wait → collect → remove).
- `PooledDockerSandbox` — borrows a pre-warmed container from the pool, executes via `docker exec`, then returns it.
- `DockerContainerFactory` — creates containers with `tail -f /dev/null` as the keep-alive command, configured with no network, memory limits, and CPU limits.

**Configuration**:
- Docker host auto-detection: Windows named pipe (`npipe:////./pipe/docker_engine`) or Unix socket (`unix:///var/run/docker.sock`).
- Custom host via `judge.docker.host`.
- HTTP transport: `ZerodepDockerHttpClient` with max 100 connections, 30s connect timeout, 45s response timeout.

**Version**: docker-java 3.3.6 (core + zerodep transport)

### 2. Apache Commons Pool2

**Role**: Docker container pooling to minimize cold-start latency.

**Usage**:
- `GenericKeyedObjectPool<String, String>` — pool keyed by Docker image name (one sub-pool per language).
- `BaseKeyedPooledObjectFactory` — creates, validates, and destroys containers.
- Pool settings: maxTotal=50, maxTotalPerKey=10, maxIdlePerKey=5, maxWait=10s, testOnBorrow=true.
- On timeout: container is invalidated (destroyed) rather than returned to pool.

**Why Pool2?**: Lightweight, mature, thread-safe, supports keyed pools out of the box.

### 3. Spring Boot 3.5.0

**Role**: Application framework providing dependency injection, REST API, configuration management, and health endpoints.

**Modules**:
- `spring-boot-starter-web` — embedded Tomcat, REST controller (`JudgeController`).
- `spring-boot-starter-actuator` — health checks, Prometheus endpoint.
- `spring-boot-starter-test` — integration test framework.

### 4. Micrometer + Prometheus

**Role**: Metrics collection and exposition for monitoring and observability.

**Metrics**:
| Metric | Type | Tags | Description |
|---|---|---|---|
| `judge.submissions.total` | Counter | `language`, `status` | Total submissions processed |
| `judge.duration` | Timer | `language` | Processing time histogram |

**Exposed at**: `GET /actuator/prometheus`

### 5. Java 21 Virtual Threads

**Role**: Lightweight concurrency for parallel test case execution.

**Usage**: Enabled globally via `spring.threads.virtual.enabled=true`. The `judgeExecutor` bean uses `SimpleAsyncTaskExecutor` with virtual threads. Each test case runs in a `CompletableFuture.supplyAsync()` on this executor.

### 6. SnakeYAML (via Spring Boot)

**Role**: Parses `languages.yaml` into `LanguageConfig` objects at startup.

### 7. Lombok

**Role**: Reduces boilerplate via `@Slf4j`, `@RequiredArgsConstructor`, `@Builder`, `@Data`.

---

## Getting Started

### Prerequisites

- **JDK 17+** (recommended: JDK 21)
- **Docker** (Docker Desktop on Windows/Mac, Docker Engine on Linux)
- **Maven** 3.8+ (or use the included Maven Wrapper)

### Quick Start

```bash
# 1. Clone and enter the project
git clone <repo-url>
cd W-judge

# 2. Start Docker (ensure daemon is running)

# 3. Build and launch
./mvnw spring-boot:run
```

The service starts on **port 9119**. Verify with:

```bash
curl http://localhost:9119/actuator/health
```

### Verify a Submission

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

## API Reference

### POST /judge — Submit Code for Judging

Submit source code with test cases for compilation and execution.

#### Request Body

```json
{
  "id": "string (required) - Unique submission identifier",
  "language": "string (required) - Language name (c, cpp, java, python, go)",
  "sourceCode": "string (required) - Source code to compile and run",
  "testCases": [
    {
      "input": "string (optional) - STDIN for this test case",
      "expectedOutput": "string (optional) - Expected STDOUT"
    }
  ],
  "timeLimit": "integer (optional) - Time limit in milliseconds",
  "memoryLimit": "integer (optional) - Memory limit in bytes"
}
```

#### Response Body

```json
{
  "submissionId": "string - Echoed from request",
  "status": "string - Final verdict",
  "timeUsed": "integer - Max time across all test cases (ms)",
  "memoryUsed": "integer - Max memory across all test cases (bytes)",
  "message": "string or null - Error message if failed",
  "testCaseResults": [
    {
      "testCaseId": "integer - 1-based index",
      "status": "string - Per-case verdict",
      "timeUsed": "integer",
      "memoryUsed": "integer",
      "output": "string - Actual STDOUT",
      "message": "string - STDERR or error detail"
    }
  ]
}
```

#### Status Codes

| Status | Meaning |
|---|---|
| `ACCEPTED` | Output matches expected output |
| `WRONG_ANSWER` | Output does not match expected output |
| `TIME_LIMIT_EXCEEDED` | Execution exceeded time limit |
| `MEMORY_LIMIT_EXCEEDED` | Execution exceeded memory limit |
| `RUNTIME_ERROR` | Non-zero exit code |
| `COMPILATION_ERROR` | Source code failed to compile |
| `SYSTEM_ERROR` | Internal system failure |
| `PENDING` | Awaiting processing |
| `JUDGING` | Currently being evaluated |

---

## Configuration

### application.yaml

| Property | Default | Description |
|---|---|---|
| `server.port` | 9119 | HTTP server port |
| `spring.threads.virtual.enabled` | true | Enable Java 21 Virtual Threads |
| `judge.pool.max-total` | 50 | Maximum total pooled containers |
| `judge.pool.max-total-per-key` | 10 | Max containers per language |
| `judge.pool.max-idle-per-key` | 5 | Max idle containers per language |
| `judge.pool.max-wait` | 10s | Max wait time for borrowing a container |
| `judge.sandbox.memory-limit` | 1GB | Default per-container memory limit |
| `judge.sandbox.nano-cpus` | 1 CPU | Default per-container CPU limit |
| `judge.compiler.time-limit` | 10000ms | Compilation timeout |
| `judge.compiler.memory-limit` | 512MB | Compilation memory limit |
| `judge.workspace.root` | /tmp/W-judge/workspace | Source code workspace directory |
| `judge.docker.host` | auto-detect | Docker daemon URI |

---

## Language Configuration

Edit `src/main/resources/languages.yaml`:

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

Supported languages out of the box:

| Language | Image | Compile | Run |
|---|---|---|---|
| C | gcc:13.2 | `gcc main.c -o main -O2 -Wall -lm -static -std=c11` | `./main` |
| C++ | gcc:13.2 | `g++ main.cpp -o main -O2 -Wall -lm -static -std=c++20` | `./main` |
| Java | eclipse-temurin:8 | `javac Main.java` | `java -Xmx256m -Xss1m Main` |
| Python | python:3.12-slim | `python3 -B -m py_compile main.py` | `python3 -B main.py` |
| Go | golang:1.26.1 | `go build -o main main.go` | `./main` |

To add a new language, add an entry to `languages.yaml` and ensure the corresponding Docker image is available.

---

## Monitoring & Metrics

Prometheus metrics are available at `http://localhost:9119/actuator/prometheus`.

### Key Metrics

```prometheus
# Counter: submissions by language and status
judge_submissions_total{language="python",status="ACCEPTED"} 42

# Histogram: processing duration in seconds
judge_duration_seconds_count{language="python"} 42
judge_duration_seconds_sum{language="python"} 12.5
```

### Health Check

```bash
curl http://localhost:9119/actuator/health
```

Access the full list of Actuator endpoints:

```bash
curl http://localhost:9119/actuator
```

---

## Project Structure

```
src/main/java/com/judge/
├── DailwJudgeApplication.java         # Spring Boot entry point
├── config/
│   ├── ContainerPoolConfig.java       # Commons Pool2 bean configuration
│   ├── DockerConfig.java              # DockerClient bean configuration
│   ├── JudgeProperties.java           # Configuration properties mapping
│   ├── LanguageConfigLoader.java      # YAML language config loader
│   └── ThreadPoolConfig.java          # Virtual thread executor config
├── controller/
│   └── JudgeController.java           # POST /judge REST endpoint
├── core/
│   ├── JudgeResult.java               # Aggregated judging result
│   ├── JudgeStatus.java               # Enum: 9 verdict states
│   ├── LanguageConfig.java            # Per-language settings
│   ├── Submission.java                # Incoming submission request
│   ├── TestCase.java                  # Single test case definition
│   └── TestCaseResult.java            # Per-test-case result
├── exception/
│   ├── CompilationException.java
│   ├── JudgeException.java
│   └── SandboxException.java
├── manager/
│   └── WorkspaceManager.java          # Temp workspace lifecycle
├── sandbox/
│   ├── DockerSandbox.java             # Fresh-container sandbox
│   ├── DockerStatsCollector.java      # Real-time memory monitoring
│   ├── PooledDockerSandbox.java       # Pooled-container sandbox (@Primary)
│   ├── Sandbox.java                   # Sandbox interface
│   ├── SandboxRequest.java            # Sandbox execution request
│   ├── SandboxResult.java             # Sandbox execution result
│   └── pool/
│       └── DockerContainerFactory.java # Pool2 object factory
└── service/
    ├── CompileResult.java             # Compilation outcome
    ├── CompilerService.java           # Compilation logic
    └── JudgeService.java              # Core judging orchestration
```

---

## Development & Testing

### Run Tests

```bash
./mvnw test
```

The integration test suite (`JudgeIntegrationTest`) includes:

| Test | Description |
|---|---|
| `testPythonHelloWorld` | Basic acceptance: correct output |
| `testTimeLimitExceeded` | Infinite loop detection |
| `testMultipleTestCases` | Multiple correct test cases |
| `testMultipleTestCasesWithOneFailure` | Partial failure → WRONG_ANSWER |
| `testConcurrentPerformance` | Verifies parallel execution (5×1s cases complete < 4s) |

### Build Package

```bash
./mvnw clean package -DskipTests
java -jar target/judge-0.0.1-SNAPSHOT.jar
```

---

## Roadmap

Potential areas for future development:

- [ ] **Special Judge (SPJ)** — Support custom comparator programs for non-strict output matching
- [ ] **Interactive Problem Support** — Real-time interaction between judge and submission
- [ ] **Submission Queue & Rate Limiting** — Prevent system overload under high concurrency
- [ ] **WebSocket Streaming** — Real-time progress updates during judging
- [ ] **Authentication & Authorization** — API key or JWT-based access control
- [ ] **Result Caching** — Cache identical submissions to skip re-execution
- [ ] **Kubernetes Nativization** — Dynamic container scheduling on K8s clusters
- [ ] **Pluggable Sandbox** — gVisor, Firecracker, or other sandbox backends beyond Docker
