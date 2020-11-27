package io.javaoperatorsdk.operator.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;

public class DefaultConfigurationService implements ConfigurationService {
    private final static ConfigurationService instance = new DefaultConfigurationService();
    private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();
    
    public static ConfigurationService instance() {
        return instance;
    }
    
    @Override
    public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(ResourceController<R> controller) {
        if (controller == null) {
            return null;
        }
        final var name = controller.getName();
        var configuration = configurations.get(name);
        if (configuration == null) {
            configuration = new AnnotationConfiguration(controller);
            configurations.put(name, configuration);
        }
        return configuration;
    }
}
