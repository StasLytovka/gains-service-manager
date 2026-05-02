package com.gains.mgr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * @SpringBootApplication combines three annotations:
 * @Configuration - marks this class as a source of bean definitions
 * @EnableAutoConfiguration - auto-configures beans based on classpath
 * @ComponentScan - scans this package for @Component, @Service, etc.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
