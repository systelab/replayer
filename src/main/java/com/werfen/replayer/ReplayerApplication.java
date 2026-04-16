package com.werfen.replayer;

import com.werfen.replayer.config.ReplayerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ReplayerProperties.class)
public class ReplayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReplayerApplication.class, args);
    }
}
