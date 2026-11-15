package dk.dsy1.chopshop.registration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication public class RegistrationServiceApplication
{

  public static void main(String[] args)
  {
    SpringApplication.run(RegistrationServiceApplication.class, args);
  }

}
