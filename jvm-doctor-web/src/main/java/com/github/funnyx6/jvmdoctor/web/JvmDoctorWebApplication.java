package com.github.funnyx6.jvmdoctor.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JvmDoctorWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(JvmDoctorWebApplication.class, args);
    }
}
