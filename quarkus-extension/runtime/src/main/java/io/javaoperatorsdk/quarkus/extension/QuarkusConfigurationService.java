package io.javaoperatorsdk.quarkus.extension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

public class QuarkusConfigurationService implements ConfigurationService {
    private final Map<String, ControllerConfiguration> controllerConfigurations;
    private final KubernetesClient client;
    
    public QuarkusConfigurationService(List<ControllerConfiguration> configurations, KubernetesClient client) {
        this.client = client;
        if (configurations != null && !configurations.isEmpty()) {
            controllerConfigurations = new ConcurrentHashMap<>(configurations.size());
            configurations.forEach(c -> controllerConfigurations.put(c.getName(), c));
        } else {
            controllerConfigurations = Collections.emptyMap();
        }
    }
    
    @Override
    public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(ResourceController<R> controller) {
        return controllerConfigurations.get(controller.getName());
    }
    
    @Override
    public Config getClientConfiguration() {
        return client.getConfiguration();
    }
}
