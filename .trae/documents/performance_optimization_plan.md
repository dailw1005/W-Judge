# 性能优化方案 (Performance Optimization Plan)

## 摘要 (Summary)
优化在线判题系统 (Judge System) 的核心性能，重点减少不必要的 Docker API 调用、降低网络及进程开销，并在高并发场景下减少内存碎片。该方案在保留原有内存统计的基础上，对容器连接池、清理机制及输出流收集进行重构，以大幅提升并发吞吐量。

## 当前状态分析 (Current State Analysis)
1. **连接池性能瓶颈**：`ContainerPoolConfig` 中开启了 `testOnBorrow`。每次从对象池借用容器都会发起一次对 Docker daemon 的状态检查请求，导致每次测试用例执行前都有几百毫秒的阻塞延迟。
2. **多余的清理调用**：`PooledDockerSandbox` 在每次运行完成后，通过单独调用 `docker exec` 执行 `rm -rf /tmp/*` 来清理临时目录。这相当于让系统执行两遍完整的 Docker 命令生命周期，严重拉低了判题速度。
3. **流收集效率低**：在收集标准输出和标准错误时，`PooledDockerSandbox` 使用 `StringBuilder` 频繁进行字符串编码拼接 (`new String(item.getPayload(), StandardCharsets.UTF_8)`)，在产生大量输出时极易引发内存碎片和 GC 压力。

## 方案详情 (Proposed Changes)

1. **修改 `src/main/java/com/judge/config/ContainerPoolConfig.java`**
   - **变更**：关闭 `testOnBorrow` 借用检查，改为后台空闲检查。
   - **原因**：避免每次判题时都等待 Docker API 的校验返回。如果借用到了死掉的容器，后续的 `execCreateCmd` 也会抛出异常，此时沙箱已有的 catch 逻辑会自动调用 `invalidateObject`，容错率足够。
   - **具体做法**：
     - `config.setTestOnBorrow(false);`
     - `config.setTestWhileIdle(true);`
     - 增加时间配置 `config.setTimeBetweenEvictionRunsMillis(60000);` (60秒)。

2. **修改 `src/main/java/com/judge/sandbox/PooledDockerSandbox.java`**
   - **变更 1**：移除原有的独立清理机制。
     - **原因**：我们将在用户执行脚本中直接内联清理命令。
     - **具体做法**：删除 `finally` 块中的 `cleanupContainer(containerId);`，并彻底删除 `cleanupContainer` 方法。
   - **变更 2**：优化标准流收集方式。
     - **原因**：减少内存碎片，加快大规模输出场景的处理速度。
     - **具体做法**：将 `StringBuilder` 替换为 `ByteArrayOutputStream`。在 `onNext` 方法中直接写入 byte 数组，结束后统一使用 `stdout.toString(StandardCharsets.UTF_8.name())` 转为字符串。
   - **变更 3**：保留原有的 `DockerStatsCollector` 内存收集机制（遵循之前决策）。

3. **修改 `src/main/java/com/judge/service/JudgeService.java`**
   - **变更**：在执行测试用例的命令行中，内联追加 `rm -rf /tmp/*` 清理命令。
   - **原因**：通过在同一条 bash 命令中完成运行和清理，消除额外的 `docker exec` 调用开销。
   - **具体做法**：将 `fullCmd` 的组装逻辑更新为：
     `String fullCmd = String.format("cd /app/%s && %s < %s; CODE=$?; chmod -R 777 .; rm -rf /tmp/*; exit $CODE", workDirName, runCmd, inputFileName);`

4. **修改 `src/main/java/com/judge/service/CompilerService.java`**
   - **变更**：在编译代码的命令行中，同样内联追加 `rm -rf /tmp/*` 清理命令。
   - **原因**：与运行时保持一致，编译也可能产生临时文件，一次性清理。
   - **具体做法**：将 `fullCmd` 的组装逻辑更新为：
     `String fullCmd = String.format("cd /app/%s && %s; CODE=$?; chmod -R 777 .; rm -rf /tmp/*; exit $CODE", workDirName, config.getCompileCmd());`

## 假设与决策 (Assumptions & Decisions)
- **决策**：我们选择保留 `DockerStatsCollector` 来统计内存，放弃直接移除它的激进方案。这保证了业务逻辑的完整性（能够上报并限制内存）。
- **假设**：由于采用内联清理（即在 bash 执行命令的最后加上 `rm -rf /tmp/*`），如果用户代码超时或被宿主机 kill，导致这条 bash 没有执行到最后，临时文件可能没有被清理。但容器连接池策略已能确保超时异常会被捕获并且调用 `pool.invalidateObject` 废弃该容器（`PooledDockerSandbox` 行 76），被废弃的容器会被 `DockerContainerFactory` 销毁并重新创建，因此并不会引发资源泄露或状态污染。

## 验证步骤 (Verification Steps)
- 使用 `mvn test -Dtest=JudgeIntegrationTest#testConcurrentPerformance` 验证优化前后的并发执行耗时，期待有明显的耗时缩减。
- 确保集成测试中的其他所有普通逻辑（如 `testPythonHelloWorld` 和 `testTimeLimitExceeded`）都能通过，证明重构不影响正常功能及超时处理逻辑。