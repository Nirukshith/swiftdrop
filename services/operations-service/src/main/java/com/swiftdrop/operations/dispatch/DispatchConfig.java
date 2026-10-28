package com.swiftdrop.operations.dispatch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchConfig {

    @Bean
    public WorkloadCounter workloadCounter() {
        return new WorkloadCounter();
    }
}
