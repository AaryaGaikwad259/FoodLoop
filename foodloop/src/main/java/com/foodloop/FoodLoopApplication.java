package com.foodloop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded because we authenticate with our own JWT filter
// (this also hides the "Using generated security password" log message).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FoodLoopApplication {
    public static void main(String[] args) {
        SpringApplication.run(FoodLoopApplication.class, args);
    }
}
