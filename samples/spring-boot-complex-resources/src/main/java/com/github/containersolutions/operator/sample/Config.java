package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.processing.retry.GenericRetry;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

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
    @SuppressWarnings("unchecked")
	@Bean
    public Operator operator(KubernetesClient client, List<ResourceController> controllers) {
        Operator operator = new Operator(client);
        controllers.forEach(c -> {operator.registerControllerForAllNamespaces(c,
                GenericRetry.defaultLimitedExponentialRetry());
        if (c instanceof CustomServiceController) {
        	((CustomServiceController)c).setCustomResourceClient(operator.getCustomResourceClients());
        }
        });
        return operator;
    }
    
//    @Bean
//    public Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> getCustomResourceClient(Operator operator) {
//    	return operator.getCustomResourceClients();
//    }

}
