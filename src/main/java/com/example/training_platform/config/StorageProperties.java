package com.example.training_platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /**
     * Root directory for uploaded curriculum PDFs (relative or absolute path).
     */
    private String root = "./var/storage";
}
