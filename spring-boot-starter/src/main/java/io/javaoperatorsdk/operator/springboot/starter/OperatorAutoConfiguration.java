package io.javaoperatorsdk.operator.springboot.starter;

import java.util.List;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ClientProperties.class, RetryProperties.class})
public class OperatorAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OperatorAutoConfiguration.class);
    
    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient(ClientProperties clientProperties, OperatorProperties operatorProperties) {
        return io.javaoperatorsdk.operator.config.Configuration.getClientFor(clientProperties, operatorProperties);
    }
    
    @Bean
    @ConditionalOnMissingBean(Operator.class)
    public Operator operator(KubernetesClient kubernetesClient, Retry retry, List<ResourceController> resourceControllers) {
        Operator operator = new Operator(kubernetesClient);
        resourceControllers.forEach(r -> operator.registerController(r, retry));
        return operator;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public Retry retry(RetryProperties retryProperties) {
        return io.javaoperatorsdk.operator.config.Configuration.getRetryFor(retryProperties);
    }
}
