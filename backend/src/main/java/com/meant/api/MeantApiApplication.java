package com.meant.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeantApiApplication {

    static void main(String[] args) {
        SpringApplication.run(MeantApiApplication.class, args);
    }
}
