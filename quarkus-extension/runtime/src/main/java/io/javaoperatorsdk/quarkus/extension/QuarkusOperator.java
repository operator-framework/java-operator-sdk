package io.javaoperatorsdk.quarkus.extension;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
public class QuarkusOperator {
    @Inject
    KubernetesClient k8sClient;
    
    @Inject
    ConfigurationService configurationService;
    
    private final Operator operator;
    
    public QuarkusOperator() {
        operator = new Operator(k8sClient, configurationService);
    }
    
    @Produces
    @DefaultBean
    @Singleton
    Operator operator() {
        return operator;
    }
}
