package com.judge.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class DockerStatsCollector implements Closeable {

    private final DockerClient dockerClient;
    private final String containerId;

    private final AtomicLong maxMemory = new AtomicLong(0);
    private ResultCallback.Adapter<Statistics> statsCallback;

    /**
     * Start streaming Docker stats (for long-running programs in DockerSandbox).
     */
    public void start() {
        statsCallback = new ResultCallback.Adapter<Statistics>() {
            @Override
            public void onNext(Statistics stats) {
                if (stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null) {
                    long usage = stats.getMemoryStats().getUsage();
                    maxMemory.updateAndGet(current -> Math.max(current, usage));
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (throwable instanceof java.nio.channels.ClosedByInterruptException) {
                    log.debug("Stats collection stopped due to interruption");
                    return;
                }
                log.warn("Error collecting stats for container {}", containerId, throwable);
            }
        };

        dockerClient.statsCmd(containerId).exec(statsCallback);
    }

    /**
     * Read container memory usage. Tries host cgroup path first (instant, no Docker API call),
     * falls back to reading from inside the container via a quick exec.
     *
     * Host paths tried:
     *   - cgroup v2 (systemd): /sys/fs/cgroup/system.slice/docker-{id}.scope/memory.current
     *   - cgroup v2 (cgroupfs): /sys/fs/cgroup/docker/{id}/memory.current
     */
    public long readCgroupMemory() {
        // Try host cgroup paths first (fast, no docker exec needed)
        long memory = readCgroupFromHost();
        if (memory > 0) {
            return memory;
        }

        // Fallback: read from inside container via docker exec
        return readCgroupViaExec();
    }

    private long readCgroupFromHost() {
        String[] patterns = {
            // cgroup v2 with systemd cgroup driver
            "/sys/fs/cgroup/system.slice/docker-%s.scope/memory.current",
            // cgroup v2 with cgroupfs driver
            "/sys/fs/cgroup/docker/%s/memory.current",
        };

        for (String pattern : patterns) {
            try {
                Path path = Paths.get(String.format(pattern, containerId));
                if (Files.exists(path)) {
                    String content = Files.readString(path).trim();
                    if (!content.isEmpty()) {
                        long memory = Long.parseLong(content);
                        log.debug("Cgroup memory (host) for {}: {} bytes", containerId, memory);
                        return memory;
                    }
                }
            } catch (Exception e) {
                log.debug("Host cgroup path not available: {}", pattern);
            }
        }
        return 0;
    }

    private long readCgroupViaExec() {
        try {
            StringBuilder output = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);

            ExecCreateCmdResponse memExecCmd = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(new String[]{"sh", "-c",
                            "cat /sys/fs/cgroup/memory.current"})
                    .exec();

            dockerClient.execStartCmd(memExecCmd.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame item) {
                            output.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.warn("Error reading cgroup memory for container {}", containerId, throwable);
                            latch.countDown();
                        }
                    })
                    .awaitCompletion(100, TimeUnit.MILLISECONDS);

            String memStr = output.toString().trim();
            if (!memStr.isEmpty()) {
                long memory = Long.parseLong(memStr);
                log.debug("Cgroup memory (exec) for {}: {} bytes", containerId, memory);
                return memory;
            }
        } catch (Exception e) {
            log.warn("Failed to read cgroup memory via exec for container {}", containerId, e);
        }
        return 0;
    }

    /**
     * Get max memory from streaming stats (may be 0 for short programs).
     */
    public long getMaxMemory() {
        return maxMemory.get();
    }

    @Override
    public void close() {
        if (statsCallback != null) {
            try {
                statsCallback.close();
            } catch (IOException e) {
                log.warn("Error closing stats callback for container {}", containerId, e);
            }
        }
    }
}
