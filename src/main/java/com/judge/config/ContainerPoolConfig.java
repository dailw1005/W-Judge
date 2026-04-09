package com.judge.config;

import com.judge.sandbox.pool.DockerContainerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ContainerPoolConfig {

    private final JudgeProperties judgeProperties;

    @Bean
    public GenericKeyedObjectPool<String, String> dockerContainerPool(DockerContainerFactory factory) {
        GenericKeyedObjectPoolConfig<String> config = new GenericKeyedObjectPoolConfig<>();
        config.setMaxTotalPerKey(judgeProperties.getPool().getMaxTotalPerKey());
        config.setMaxIdlePerKey(judgeProperties.getPool().getMaxIdlePerKey());
        config.setMaxTotal(judgeProperties.getPool().getMaxTotal());
        config.setBlockWhenExhausted(true);
        config.setMaxWait(judgeProperties.getPool().getMaxWait());
        config.setTestOnBorrow(false);
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRuns(java.time.Duration.ofSeconds(60));
        return new GenericKeyedObjectPool<>(factory, config);
    }
}
