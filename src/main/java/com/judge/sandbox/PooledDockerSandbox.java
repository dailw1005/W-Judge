package com.judge.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.judge.exception.SandboxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
@Primary
@Slf4j
@RequiredArgsConstructor
public class PooledDockerSandbox implements Sandbox {

    private final GenericKeyedObjectPool<String, String> pool;
    private final DockerClient dockerClient;

    @Override
    public SandboxResult run(SandboxRequest request) {
        String containerId = null;
        String imageName = request.getImageName();
        
        try {
            ensureImageExists(imageName);
            
            containerId = pool.borrowObject(imageName);
            
            // Execute command
            String[] command = request.getCommand(); 
            
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command)
                    .exec();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame item) {
                    if (item.getStreamType().name().equals("STDOUT")) {
                        stdout.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                    } else if (item.getStreamType().name().equals("STDERR")) {
                        stderr.append(new String(item.getPayload(), StandardCharsets.UTF_8));
                    }
                }
            };
            
            long startTime = System.nanoTime();

            try (DockerStatsCollector statsCollector = new DockerStatsCollector(dockerClient, containerId)) {
                statsCollector.start();

                dockerClient.execStartCmd(execCreateCmdResponse.getId())
                        .exec(callback)
                        .awaitCompletion(request.getTimeLimit(), TimeUnit.MILLISECONDS);

                long memoryUsed = statsCollector.getMaxMemory();
                long timeUsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

                InspectExecResponse inspect = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();

                if (inspect.isRunning()) {
                    // If timeout, we invalidate the container to be safe as we can't easily kill exec
                    pool.invalidateObject(imageName, containerId);
                    containerId = null;

                    return SandboxResult.builder()
                            .timeLimitExceeded(true)
                            .timeUsed(request.getTimeLimit())
                            .memoryUsed(memoryUsed)
                            .stdout(stdout.toString())
                            .stderr(stderr.toString())
                            .build();
                }

                int exitCode = inspect.getExitCode();

                return SandboxResult.builder()
                        .stdout(stdout.toString())
                        .stderr(stderr.toString())
                        .exitCode(exitCode)
                        .timeUsed(timeUsed)
                        .memoryUsed(memoryUsed)
                        .memoryLimitExceeded(false)
                        .build();
            }

        } catch (Exception e) {
            log.error("Pooled sandbox error", e);
            if (containerId != null) {
                try {
                    pool.invalidateObject(imageName, containerId);
                    containerId = null;
                } catch (Exception ex) {
                    log.error("Failed to invalidate container", ex);
                }
            }
            throw new SandboxException("Pooled sandbox execution failed", e);
        } finally {
            if (containerId != null) {
                try {
                    cleanupContainer(containerId);
                    pool.returnObject(imageName, containerId);
                } catch (Exception e) {
                    log.error("Cleanup failed, invalidating container {}", containerId, e);
                    try {
                        pool.invalidateObject(imageName, containerId);
                    } catch (Exception ex) {
                        log.error("Failed to invalidate container", ex);
                    }
                }
            }
        }
    }

    private void cleanupContainer(String containerId) throws InterruptedException {
        try {
            // Clean up /tmp
            String[] cleanupCmd = new String[]{"sh", "-c", "rm -rf /tmp/*"};
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd(cleanupCmd)
                    .withAttachStdout(false)
                    .withAttachStderr(true)
                    .exec();

            dockerClient.execStartCmd(exec.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {})
                    .awaitCompletion(5, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to cleanup container {}", containerId, e);
            throw new RuntimeException("Cleanup failed", e);
        }
    }
    
    private void ensureImageExists(String imageName) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("Image {} not found locally, pulling...", imageName);
            try {
                dockerClient.pullImageCmd(imageName)
                        .exec(new com.github.dockerjava.api.command.PullImageResultCallback())
                        .awaitCompletion();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while pulling image: " + imageName, ie);
            }
        }
    }
}
