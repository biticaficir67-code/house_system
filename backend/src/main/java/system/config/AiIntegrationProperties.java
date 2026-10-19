package system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.ai")
public record AiIntegrationProperties(String priceBaseUrl, String textBaseUrl) {
}
