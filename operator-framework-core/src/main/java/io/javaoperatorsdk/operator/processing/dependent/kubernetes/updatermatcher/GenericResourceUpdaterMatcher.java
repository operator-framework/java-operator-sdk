package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Map;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceUpdaterMatcher;

public class GenericResourceUpdaterMatcher<R extends HasMetadata> implements
    ResourceUpdaterMatcher<R> {

  private static final ResourceUpdaterMatcher<?> INSTANCE = new GenericResourceUpdaterMatcher<>();

  @SuppressWarnings("rawtypes")
  private static final Map<Class, ResourceUpdaterMatcher> processors = Map.of(
      Secret.class, new SecretResourceUpdaterMatcher(),
      ConfigMap.class, new ConfigMapResourceUpdaterMatcher(),
      ServiceAccount.class, new ServiceAccountResourceUpdaterMatcher(),
      Role.class, new RoleResourceUpdaterMatcher(),
      ClusterRole.class, new ClusterRoleResourceUpdaterMatcher(),
      RoleBinding.class, new RoleBindingResourceUpdaterMatcher(),
      ClusterRoleBinding.class, new ClusterRoleBindingResourceUpdaterMatcher(),
      Endpoints.class, new EndpointsResourceUpdaterMatcher(),
      EndpointSlice.class, new EndpointSliceResourceUpdateMatcher());

  protected GenericResourceUpdaterMatcher() {}

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> ResourceUpdaterMatcher<R> updaterMatcherFor(
      Class<R> resourceType) {
    final var processor = processors.get(resourceType);
    return processor != null ? processor : (ResourceUpdaterMatcher<R>) INSTANCE;
  }

  public R updateResource(R actual, R desired, Context<?> context) {
    var clonedActual = context.getControllerConfiguration().getConfigurationService()
        .getResourceCloner().clone(actual);
    updateLabelsAndAnnotation(clonedActual, desired);
    updateClonedActual(clonedActual, desired);
    return clonedActual;
  }

  @Override
  public boolean matches(R actual, R desired, Context<?> context) {
    return GenericKubernetesResourceMatcher.match(desired, actual, true,
        false, false, context).matched();
  }

  protected void updateClonedActual(R actual, R desired) {
    updateSpec(actual, desired);
  }

  public static <K extends HasMetadata> void updateSpec(K actual, K desired) {
    var desiredSpec = ReconcilerUtils.getSpec(desired);
    ReconcilerUtils.setSpec(actual, desiredSpec);
  }

  public static <K extends HasMetadata> void updateLabelsAndAnnotation(K actual, K desired) {
    actual.getMetadata().getLabels().putAll(desired.getMetadata().getLabels());
    actual.getMetadata().getAnnotations().putAll(desired.getMetadata().getAnnotations());
  }

}
