package com.agv.dispatch.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.agv.dispatch")
@EntityScan(basePackages = "com.agv.dispatch.common.entity")
@EnableJpaRepositories(basePackages = "com.agv.dispatch.core.repository")
@EnableScheduling
@EnableAsync
public class AgvDispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgvDispatchApplication.class, args);
    }
}
