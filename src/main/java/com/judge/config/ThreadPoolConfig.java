package com.judge.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class ThreadPoolConfig {

    private final JudgeProperties judgeProperties;

    @Bean(name = "judgeExecutor")
    public Executor judgeExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix(judgeProperties.getThread().getThreadNamePrefix());
        return executor;
    }
}
