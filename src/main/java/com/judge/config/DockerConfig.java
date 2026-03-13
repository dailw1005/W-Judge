package com.judge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
@Slf4j
public class DockerConfig {

    @Value("${judge.docker.host:}")
    private String dockerHost;

    @Bean
    public DockerClient dockerClient() {
        String host = dockerHost;
        if (host == null || host.isEmpty()) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                host = "npipe:////./pipe/docker_engine";
            } else {
                host = "unix:///var/run/docker.sock";
            }
        }

        log.info("Initializing Docker Client with host: {}", host);

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(host)
                .build();
        
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
