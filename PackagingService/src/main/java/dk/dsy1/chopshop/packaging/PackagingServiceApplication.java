package dk.dsy1.chopshop.packaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class PackagingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PackagingServiceApplication.class, args);
    }
}
