package io.javaoperatorsdk.operator.springboot.starter;

import io.javaoperatorsdk.operator.config.RetryConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operator.controller.retry")
public class RetryProperties extends RetryConfiguration {
}
