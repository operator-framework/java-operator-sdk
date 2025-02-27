package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.health.ControllerHealthInfo;

public interface RegisteredController<P extends HasMetadata> extends NamespaceChangeable {

  ControllerConfiguration<P> getConfiguration();

  ControllerHealthInfo getControllerHealthInfo();
}
