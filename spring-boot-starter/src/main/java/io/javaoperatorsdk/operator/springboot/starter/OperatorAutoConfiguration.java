package io.javaoperatorsdk.operator.springboot.starter;

import java.util.List;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OperatorProperties.class, RetryProperties.class})
public class OperatorAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OperatorAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient(OperatorProperties operatorProperties) {
        ConfigBuilder config = new ConfigBuilder();
        config.withTrustCerts(operatorProperties.isTrustSelfSignedCertificates());
        if (StringUtils.isNotBlank(operatorProperties.getUsername())) {
            config.withUsername(operatorProperties.getUsername());
        }
        if (StringUtils.isNotBlank(operatorProperties.getPassword())) {
            config.withUsername(operatorProperties.getPassword());
        }
        if (StringUtils.isNotBlank(operatorProperties.getMasterUrl())) {
            config.withMasterUrl(operatorProperties.getMasterUrl());
        }
        return operatorProperties.isOpenshift() ? new DefaultOpenShiftClient(config.build()) : new DefaultKubernetesClient(config.build());
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
        GenericRetry retry = new GenericRetry();
        if (retryProperties.getInitialInterval() != null) {
            retry.setInitialInterval(retryProperties.getInitialInterval());
        }
        if (retryProperties.getIntervalMultiplier() != null) {
            retry.setIntervalMultiplier(retryProperties.getIntervalMultiplier());
        }
        if (retryProperties.getMaxAttempts() != null) {
            retry.setMaxAttempts(retryProperties.getMaxAttempts());
        }
        if (retryProperties.getMaxInterval() != null) {
            retry.setInitialInterval(retryProperties.getMaxInterval());
        }
        return retry;
    }
}
