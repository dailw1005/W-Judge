package com.judge.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@RequiredArgsConstructor
public class ThreadPoolConfig {

    private final JudgeProperties judgeProperties;

    @Bean(name = "judgeExecutor")
    public Executor judgeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(judgeProperties.getThread().getCorePoolSize());
        executor.setMaxPoolSize(judgeProperties.getThread().getMaxPoolSize());
        executor.setQueueCapacity(judgeProperties.getThread().getQueueCapacity());
        executor.setThreadNamePrefix(judgeProperties.getThread().getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
