package com.minicoze.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PlatformApiApplication {
    public static void main(String[] args) { SpringApplication.run(PlatformApiApplication.class, args); }
}
