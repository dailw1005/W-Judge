package com.judge.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.judge.exception.SandboxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class DockerSandbox implements Sandbox {

    private final DockerClient dockerClient;

    @Override
    public SandboxResult run(SandboxRequest request) {
        String containerId = null;
        try {
            ensureImageExists(request.getImageName());

            // Configure volumes
            List<Bind> binds = new ArrayList<>();
            if (request.getVolumeMounts() != null) {
                binds = request.getVolumeMounts().entrySet().stream()
                        .map(entry -> new Bind(entry.getKey(), new Volume(entry.getValue())))
                        .collect(Collectors.toList());
            }

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(request.getMemoryLimit() > 0 ? request.getMemoryLimit() : 256 * 1024 * 1024L)
                    .withMemorySwap(request.getMemoryLimit() > 0 ? request.getMemoryLimit() : 256 * 1024 * 1024L)
                    .withNanoCPUs(1_000_000_000L) // 1 CPU
                    .withNetworkMode(request.isNetworkDisabled() ? "none" : "bridge")
                    .withBinds(binds)
                    .withReadonlyRootfs(false); 

            CreateContainerResponse container = dockerClient.createContainerCmd(request.getImageName())
                    .withName(request.getContainerName())
                    .withHostConfig(hostConfig)
                    .withCmd(request.getCommand())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(false)
                    .withUser("nobody")
                    .exec();

            containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            // Asynchronous logging
            ResultCallback.Adapter<Frame> logCallback = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame item) {
                    String payload = new String(item.getPayload(), StandardCharsets.UTF_8);
                    if (item.getStreamType().name().equals("STDOUT")) {
                        stdout.append(payload);
                    } else if (item.getStreamType().name().equals("STDERR")) {
                        stderr.append(payload);
                    }
                }
            };
            
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(logCallback);

            long startTime = System.nanoTime();

            // Start stats collection
            DockerStatsCollector statsCollector = new DockerStatsCollector(dockerClient, containerId);
            statsCollector.start();

            // Wait for container
            WaitContainerResultCallback waitCallback =
                dockerClient.waitContainerCmd(containerId).start();

            boolean finished = waitCallback.awaitCompletion(request.getTimeLimit(), TimeUnit.MILLISECONDS);

            statsCollector.close();
            long memoryUsed = statsCollector.getMaxMemory();

            long timeUsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            if (!finished) {
                try {
                    dockerClient.killContainerCmd(containerId).exec();
                } catch (Exception e) {
                    // ignore if already stopped
                }
                return SandboxResult.builder()
                        .timeLimitExceeded(true)
                        .timeUsed(request.getTimeLimit())
                        .memoryUsed(memoryUsed)
                        .stdout(stdout.toString())
                        .stderr(stderr.toString())
                        .build();
            }

            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            boolean oom = inspect.getState().getOOMKilled() != null && inspect.getState().getOOMKilled();
            int exitCode = inspect.getState().getExitCodeLong() != null ? inspect.getState().getExitCodeLong().intValue() : 0;

            return SandboxResult.builder()
                    .stdout(stdout.toString())
                    .stderr(stderr.toString())
                    .exitCode(exitCode)
                    .timeUsed(timeUsed)
                    .memoryUsed(memoryUsed)
                    .memoryLimitExceeded(oom)
                    .build();

        } catch (Exception e) {
            log.error("Docker sandbox error", e);
            throw new SandboxException("Docker sandbox execution failed", e);
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception e) {
                    log.error("Failed to remove container {}", containerId, e);
                }
            }
        }
    }

    private void ensureImageExists(String imageName) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("Pulling image {}", imageName);
            try {
                dockerClient.pullImageCmd(imageName).exec(new com.github.dockerjava.api.command.PullImageResultCallback()).awaitCompletion();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }
    }
}
