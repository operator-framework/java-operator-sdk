package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import java.util.List;
import java.util.function.Supplier;

@Recorder
public class ConfigurationServiceRecorder {

  public Supplier<ConfigurationService> configurationServiceSupplier(
      Version version, List<ControllerConfiguration> controllerConfigs) {
    return () ->
        new QuarkusConfigurationService(
            version, controllerConfigs, Arc.container().instance(KubernetesClient.class).get());
  }
}
