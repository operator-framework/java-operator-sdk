package com.github.containersolutions.operator.spingboot.starter;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;

@Configuration
@EnableConfigurationProperties(OperatorProperties.class)
@ConditionalOnMissingBean(Operator.class)
public class OperatorAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OperatorAutoConfiguration.class);

    @Autowired
    private GenericApplicationContext genericApplicationContext;

    @Autowired
    private OperatorProperties operatorProperties;

    @Autowired
    private List<ResourceController> resourceControllers;

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient() {
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
        KubernetesClient k8sClient = operatorProperties.isOpenshift() ? new DefaultOpenShiftClient(config.build()) : new DefaultKubernetesClient(config.build());
        return k8sClient;
    }

    @Bean
    public Operator operator(KubernetesClient kubernetesClient) {
        Operator operator = new Operator(kubernetesClient);
        resourceControllers.forEach(r -> operator.registerController(r));
        operator.getCustomResourceClients().entrySet().forEach(e -> {
            // todo ensure these are registered very early
            log.info("Registering CustomResourceOperationsImpl for kind: {}", e.getValue().getKind());
            genericApplicationContext.registerBean(e.getValue().getKind(), CustomResourceOperationsImpl.class,
                    () -> operator.getCustomResourceClients(e.getKey()));
        });
        return operator;
    }
}
