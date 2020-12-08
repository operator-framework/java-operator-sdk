package io.javaoperatorsdk.quarkus.extension.deployment;

import io.javaoperatorsdk.quarkus.extension.QuarkusControllerConfiguration;
import io.quarkus.builder.item.MultiBuildItem;

public final class ControllerConfigurationBuildItem extends MultiBuildItem {
    private final QuarkusControllerConfiguration configuration;
    
    public ControllerConfigurationBuildItem(QuarkusControllerConfiguration configuration) {
        this.configuration = configuration;
    }
    
    public QuarkusControllerConfiguration getConfiguration() {
        return configuration;
    }
}
