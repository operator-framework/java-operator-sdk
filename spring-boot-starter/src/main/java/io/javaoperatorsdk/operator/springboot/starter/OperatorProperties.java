package io.javaoperatorsdk.operator.springboot.starter;

import io.javaoperatorsdk.operator.config.OperatorConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operator")
public class OperatorProperties extends OperatorConfiguration {
}
