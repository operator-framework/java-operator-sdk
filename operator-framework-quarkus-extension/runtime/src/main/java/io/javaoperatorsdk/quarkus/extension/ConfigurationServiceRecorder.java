package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import java.util.List;
import java.util.function.Supplier;

@Recorder
public class ConfigurationServiceRecorder {

  public Supplier<ConfigurationService> configurationServiceSupplier(
      List<ControllerConfiguration> controllerConfigs) {
    return () ->
        new QuarkusConfigurationService(
            controllerConfigs, Arc.container().instance(KubernetesClient.class).get());
  }
}
