package edu.hnu.deepaudit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("edu.hnu.deepaudit.mapper")
public class DeepAuditBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeepAuditBackendApplication.class, args);
    }

}
