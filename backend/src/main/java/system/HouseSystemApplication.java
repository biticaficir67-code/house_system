package system;

import system.config.AiIntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AiIntegrationProperties.class)
public class HouseSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HouseSystemApplication.class, args);
    }
}
