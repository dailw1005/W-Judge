package com.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "judge")
public class JudgeProperties {

    private Pool pool = new Pool();
    private Thread thread = new Thread();
    private Sandbox sandbox = new Sandbox();
    private Compiler compiler = new Compiler();
    private Workspace workspace = new Workspace();

    @Data
    public static class Pool {
        private int maxTotalPerKey = 10;
        private int maxIdlePerKey = 5;
        private int maxTotal = 50;
        private Duration maxWait = Duration.ofSeconds(10);
    }

    @Data
    public static class Thread {
        private String threadNamePrefix = "judge-exec-";
    }

    @Data
    public static class Sandbox {
        private long memoryLimit = 1024 * 1024 * 1024L; // 1GB
        private long nanoCpus = 1_000_000_000L; // 1 CPU
    }

    @Data
    public static class Compiler {
        private long timeLimit = 10000L; // 10s
        private long memoryLimit = 512 * 1024 * 1024L; // 512MB
    }

    @Data
    public static class Workspace {
        private String root = "/tmp/W-judge/workspace";
    }
}
