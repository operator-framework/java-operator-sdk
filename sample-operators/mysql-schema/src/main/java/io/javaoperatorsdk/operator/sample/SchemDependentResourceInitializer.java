package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceInitializer;

public class SchemDependentResourceInitializer
    implements DependentResourceInitializer<SchemaDependentResource, ResourcePollerConfigService> {

  private ResourcePollerConfigService configService;

  @Override
  public void useConfigService(ResourcePollerConfigService configService) {
    this.configService = configService;
  }

  @Override
  public SchemaDependentResource initialize(
      Class<SchemaDependentResource> resourceClass,
      ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client) {

    int pollPeriod = resourceClass.getAnnotation(ResourcePoller.class).pollPeriod();
    MySQLDbConfig mySQLDbConfig = configService.getMySQLDbConfig();
    if (configService.getPollPeriod().isPresent()) {
      pollPeriod = configService.getPollPeriod().get();
    }
    return new SchemaDependentResource(mySQLDbConfig, pollPeriod);
  }
}
