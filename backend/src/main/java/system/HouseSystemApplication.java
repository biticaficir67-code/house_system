package system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import system.config.AiIntegrationProperties;
import system.report.ReportCustomController;

@SpringBootApplication(
        exclude =
                org.springframework.boot.autoconfigure.security.servlet
                        .UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(AiIntegrationProperties.class)
// ReportController serves the adjusted routes; keep the team's original source unmodified.
@ComponentScan(
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(
                    type = FilterType.CUSTOM,
                    classes = AutoConfigurationExcludeFilter.class),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = ReportCustomController.class)
        })
public class HouseSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HouseSystemApplication.class, args);
    }
}
