package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Map;

import javax.management.relation.Role;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdatePreProcessor;

public class GenericResourceUpdatePreProcessor<R extends HasMetadata> implements
    ResourceUpdatePreProcessor<R> {
  private static final ResourceUpdatePreProcessor<?> INSTANCE =
      new GenericResourceUpdatePreProcessor<>();

  @SuppressWarnings("rawtypes")
  private static final Map<Class, ResourceUpdatePreProcessor> processors = Map.of(
      Secret.class, new SecretResourceUpdatePreProcessor(),
      ConfigMap.class, new ConfigMapResourceUpdatePreProcessor(),
      ServiceAccount.class, new ServiceAccountResourceUpdateProcessor(),
      Role.class, new RoleResourceUpdatePreProcessor(),
      ClusterRole.class, new ClusterRoleResourceUpdatePreProcessor(),
      RoleBinding.class, new RoleBindingResourceUpdatePreProcessor(),
      ClusterRoleBinding.class, new ClusterRoleBindingResourceUpdatePreProcessor());

  protected GenericResourceUpdatePreProcessor() {}

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> ResourceUpdatePreProcessor<R> processorFor(
      Class<R> resourceType) {
    final var processor = processors.get(resourceType);
    return processor != null ? processor : (ResourceUpdatePreProcessor<R>) INSTANCE;
  }

  public R replaceSpecOnActual(R actual, R desired, Context<?> context) {
    var clonedActual = ConfigurationServiceProvider.instance().getResourceCloner().clone(actual);
    updateClonedActual(clonedActual, desired);
    return clonedActual;
  }

  protected void updateClonedActual(R actual, R desired) {
    var desiredSpec = ReconcilerUtils.getSpec(desired);
    ReconcilerUtils.setSpec(actual, desiredSpec);
  }
}
