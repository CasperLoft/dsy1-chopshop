package dk.dsy1.chopshop.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class ProcessingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessingServiceApplication.class, args);
    }
}
