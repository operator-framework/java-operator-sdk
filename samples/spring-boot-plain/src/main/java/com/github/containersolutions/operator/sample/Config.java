package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.processing.retry.GenericRetry;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class Config {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new DefaultKubernetesClient();
    }

    @Bean
    public CustomServiceController customServiceController(KubernetesClient client) {
        return new CustomServiceController(client);
    }

    //  Register all controller beans
    @Bean
    public Operator operator(KubernetesClient client, List<ResourceController> controllers) {
        Operator operator = new Operator(client);
        controllers.forEach(c -> operator.registerControllerForAllNamespaces(c,
                GenericRetry.defaultLimitedExponentialRetry()));
        return operator;
    }

}
