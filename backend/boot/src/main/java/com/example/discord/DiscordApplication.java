package com.example.discord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DiscordApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscordApplication.class, args);
    }
}
