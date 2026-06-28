package com.smartwealth.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SmartWealthAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWealthAiApplication.class, args);
    }
}
