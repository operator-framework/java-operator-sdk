package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.ResourceController;

public abstract class AbstractConfiguration<R extends CustomResource>
    implements ControllerConfiguration<R> {
  private final Class<? extends ResourceController<R>> controllerClass;
  private final Class<R> customResourceClass;
  private ConfigurationService service;

  public AbstractConfiguration(
      Class<? extends ResourceController<R>> controllerClass, Class<R> customResourceClass) {
    this.controllerClass = controllerClass;
    this.customResourceClass = customResourceClass;
  }

  @Override
  public String getName() {
    return ControllerUtils.getNameFor(controllerClass);
  }

  @Override
  public String getCRDName() {
    return CustomResource.getCRDName(getCustomResourceClass());
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return service;
  }

  @Override
  public void setConfigurationService(ConfigurationService service) {
    this.service = service;
  }

  @Override
  public String getAssociatedControllerClassName() {
    return controllerClass.getCanonicalName();
  }

  @Override
  public Class<R> getCustomResourceClass() {
    return customResourceClass;
  }
}
