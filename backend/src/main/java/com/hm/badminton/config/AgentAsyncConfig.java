package com.hm.badminton.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AgentAsyncConfig {

    /** Bounded pool for blocking AMap/MySQL tool calls; avoids using the global common pool. */
    @Bean(name = "agentToolExecutor")
    public Executor agentToolExecutor(AgentProperties properties) {
        int threads = Math.max(2, Math.min(properties.getToolThreads(), 12));
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("agent-tool-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
