package com.college.events.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Highly optimized thread pool for executing background tasks such as
     * Email Dispatching, Certificate Generation, and Bulk Operations.
     * Prevents the main Tomcat HTTP worker threads from blocking under heavy load.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Base threads kept alive
        executor.setCorePoolSize(50);
        // Max threads to scale up to when queue is full
        executor.setMaxPoolSize(500);
        // Number of tasks to queue before rejecting or scaling up
        executor.setQueueCapacity(10000);
        // Prefix for easier debugging in logs
        executor.setThreadNamePrefix("EventHub-Async-");
        executor.initialize();
        return executor;
    }
}
