package com.judge.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
public class DockerStatsCollector implements Closeable {

    private final DockerClient dockerClient;
    private final String containerId;
    
    private final AtomicLong maxMemory = new AtomicLong(0);
    private ResultCallback.Adapter<Statistics> statsCallback;

    public void start() {
        statsCallback = new ResultCallback.Adapter<>() {
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
