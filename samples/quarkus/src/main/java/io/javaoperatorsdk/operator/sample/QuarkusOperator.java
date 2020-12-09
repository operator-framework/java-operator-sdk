package io.javaoperatorsdk.operator.sample;

import javax.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class QuarkusOperator implements QuarkusApplication {
    
    @Inject
    KubernetesClient client;
    
    @Inject
    Operator operator;
    
    @Inject
    ConfigurationService configuration;
    
    public static void main(String... args) {
        Quarkus.run(QuarkusOperator.class, args);
    }
    
    @Override
    public int run(String... args) throws Exception {
        System.out.println("operator = " + operator);
        final var config = configuration.getConfigurationFor(new CustomServiceController(client));
        System.out.println("CR class: " + config.getCustomResourceClass());
        System.out.println("Doneable class = " + config.getDoneableClass());
        Quarkus.waitForExit();
        return 0;
    }
}