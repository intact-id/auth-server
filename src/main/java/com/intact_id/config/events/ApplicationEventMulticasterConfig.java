package com.intact_id.config.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * Configuration for async event processing with security context propagation
 */
@Configuration
public class ApplicationEventMulticasterConfig {

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();

        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(10);
        threadPoolTaskExecutor.setMaxPoolSize(30);
        threadPoolTaskExecutor.setQueueCapacity(100);
        threadPoolTaskExecutor.setThreadNamePrefix("event-async-");
        threadPoolTaskExecutor.initialize();

        // Wrap with security context propagation
        DelegatingSecurityContextAsyncTaskExecutor taskExecutor = new DelegatingSecurityContextAsyncTaskExecutor(
                threadPoolTaskExecutor);

        eventMulticaster.setTaskExecutor(taskExecutor);
        return eventMulticaster;
    }
}
