package io.javaoperatorsdk.operator.sample;

import java.util.List;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        controllers.forEach(c -> operator.registerController(c, GenericRetry.defaultLimitedExponentialRetry()));
        return operator;
    }

}
