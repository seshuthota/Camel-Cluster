package com.example.coordinator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
    "com.example.coordinator",
    "com.example.common"
})
@EntityScan(basePackages = "com.example.common.model")
@EnableJpaRepositories(basePackages = "com.example.coordinator.repository")
@EnableScheduling
public class CoordinatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoordinatorApplication.class, args);
    }
} 