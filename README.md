# Universal Code Execution Module (dailw-judge)

This module is a high-performance, secure, and scalable online judge engine designed to execute code in multiple programming languages. It leverages Docker for sandboxing and Java 21 Virtual Threads for high concurrency.

## Features

- **Multi-Language Support**: Supports C, C++, Java, Python, Go, and more. Configurable via `src/main/resources/languages.yaml`.
- **Sandboxed Execution**: Uses Docker containers to isolate execution environments, ensuring security.
- **Container Pooling**: Implements a robust Docker container pool (using Apache Commons Pool2) to reuse containers, significantly reducing startup latency.
- **Concurrent Execution**: Supports concurrent execution of multiple test cases for a single submission using Java `CompletableFuture` and a custom thread pool.
- **Multi-Testcase Support**: Capable of running multiple input/output test cases per submission.
- **Resource Limits**: Configurable CPU, Memory, and Time limits per language or submission.
- **Observability**: Integrated with Micrometer and Prometheus for real-time metrics monitoring.

## Architecture

- **Core Service**: Spring Boot 3.2 application.
- **Sandbox**: Docker-based execution environment.
- **Concurrency**:
    - **Virtual Threads**: Enabled for high-throughput I/O.
    - **Container Pool**: Reuses "warm" containers to avoid cold start overhead.
    - **Async Judge**: Parallel execution of test cases.
- **Configuration**: Centralized configuration via `application.yaml` and `JudgeProperties`.

## Prerequisites

- **JDK 17+** (Developed with JDK 21 features in mind, compatible with 17)
- **Docker Desktop** (Windows) or **Docker Engine** (Linux)
    - *Note*: Ensure the Docker daemon is exposed or configured correctly for `docker-java`.
- **Maven** 3.8+

## Configuration

### Application Settings
Edit `src/main/resources/application.yaml` to configure:
- **Server Port**: Default is `9119`.
- **Judge Settings**:
    - Pool size (`judge.pool.max-total`)
    - Thread pool size (`judge.thread.core-pool-size`)
    - Sandbox limits (`judge.sandbox.memory-limit`)
    - Workspace path (`judge.workspace.root`)

### Language Support
Edit `src/main/resources/languages.yaml` to add or modify supported languages, docker images, and compile/run commands.

## Running

1. **Start Docker**: Ensure your Docker environment is running.
2. **Build & Run**:
   ```bash
   mvn spring-boot:run
   ```
   The service will start on port `9119`.

## API Usage

### Submit Code
**POST** `/judge`

Submit a solution with multiple test cases.

**Request Body**:
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

**Response**:
```json
{
  "submissionId": "submission-uuid-123",
  "status": "ACCEPTED",
  "timeUsed": 150,
  "memoryUsed": 0,
  "message": null,
  "testCaseResults": [
    {
      "testCaseId": 1,
      "status": "ACCEPTED",
      "timeUsed": 120,
      "memoryUsed": 0,
      "output": "hello\n",
      "message": ""
    },
    {
      "testCaseId": 2,
      "status": "ACCEPTED",
      "timeUsed": 150,
      "memoryUsed": 0,
      "output": "world\n",
      "message": ""
    }
  ]
}
```

## Metrics

Prometheus metrics are exposed at `http://localhost:9119/actuator/prometheus`.

Key metrics:
- `judge_submissions_total`: Counter of total submissions processed.
- `judge_duration_seconds`: Histogram of processing time.

## Project Structure

- `com.judge.core`: Domain models (Submission, JudgeResult, TestCase).
- `com.judge.service`: Business logic (JudgeService, CompilerService).
- `com.judge.sandbox`: Docker sandbox implementation and pooling logic.
- `com.judge.manager`: File system and workspace management.
- `com.judge.config`: Application configuration.
- `com.judge.exception`: Custom exception handling.
