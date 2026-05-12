package com.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.sync.config")
public class SynctripApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynctripApplication.class, args);
    }

}
