package io.javaoperatorsdk.operator.dependent.dependentannotationsecondarymapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(
    dependents =
        @Dependent(
            type = DependentAnnotationSecondaryMapperReconciler.ConfigMapDependentResource.class))
@ControllerConfiguration
public class DependentAnnotationSecondaryMapperReconciler
    implements Reconciler<DependentAnnotationSecondaryMapperResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DependentAnnotationSecondaryMapperResource> reconcile(
      DependentAnnotationSecondaryMapperResource resource,
      Context<DependentAnnotationSecondaryMapperResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public static class ConfigMapDependentResource
      extends KubernetesDependentResource<ConfigMap, DependentAnnotationSecondaryMapperResource>
      implements Creator<ConfigMap, DependentAnnotationSecondaryMapperResource>,
          Updater<ConfigMap, DependentAnnotationSecondaryMapperResource>,
          Deleter<DependentAnnotationSecondaryMapperResource> {

    @Override
    protected ConfigMap desired(
        DependentAnnotationSecondaryMapperResource primary,
        Context<DependentAnnotationSecondaryMapperResource> context) {
      ConfigMap configMap = new ConfigMap();
      configMap.setMetadata(
          new ObjectMetaBuilder()
              .withName(primary.getMetadata().getName())
              .withNamespace(primary.getMetadata().getNamespace())
              .build());
      configMap.setData(Map.of("data", primary.getMetadata().getName()));
      return configMap;
    }
  }
}
