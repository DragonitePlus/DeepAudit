package edu.hnu.deepaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DeepAuditBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepAuditBackendApplication.class, args);
    }

}
