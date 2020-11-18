package io.javaoperatorsdk.operator.springboot.starter;

import io.javaoperatorsdk.operator.config.ClientConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operator.kubernetes.client")
public class ClientProperties extends ClientConfiguration {
}
