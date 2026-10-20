package system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import system.config.AiIntegrationProperties;

@SpringBootApplication(
        exclude =
                org.springframework.boot.autoconfigure.security.servlet
                        .UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AiIntegrationProperties.class)
public class HouseSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HouseSystemApplication.class, args);
    }
}
