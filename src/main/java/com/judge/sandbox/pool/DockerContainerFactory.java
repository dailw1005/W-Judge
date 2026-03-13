package com.judge.sandbox.pool;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.judge.config.JudgeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DockerContainerFactory extends BaseKeyedPooledObjectFactory<String, String> {

    private final DockerClient dockerClient;
    private final JudgeProperties judgeProperties;

    private static final String CONTAINER_WORK_DIR = "/app";

    @Override
    public String create(String imageName) throws Exception {
        log.info("Creating new container for image: {}", imageName);
        
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(judgeProperties.getSandbox().getMemoryLimit())
                .withNanoCPUs(judgeProperties.getSandbox().getNanoCpus())
                .withBinds(new Bind(judgeProperties.getWorkspace().getRoot(), new Volume(CONTAINER_WORK_DIR)))
                .withNetworkMode("none");

        CreateContainerResponse response = dockerClient.createContainerCmd(imageName)
                .withCmd("tail", "-f", "/dev/null") // Keep running
                .withHostConfig(hostConfig)
                .exec();

        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();
        
        log.info("Container created and started: {}", containerId);
        return containerId;
    }

    @Override
    public PooledObject<String> wrap(String containerId) {
        return new DefaultPooledObject<>(containerId);
    }

    @Override
    public void destroyObject(String key, PooledObject<String> p) throws Exception {
        String containerId = p.getObject();
        log.info("Destroying container: {}", containerId);
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container {}", containerId, e);
        }
    }

    @Override
    public boolean validateObject(String key, PooledObject<String> p) {
        String containerId = p.getObject();
        try {
            Boolean running = dockerClient.inspectContainerCmd(containerId)
                    .exec()
                    .getState()
                    .getRunning();
            return running != null && running;
        } catch (Exception e) {
            log.warn("Validation failed for container {}", containerId, e);
            return false;
        }
    }
}
