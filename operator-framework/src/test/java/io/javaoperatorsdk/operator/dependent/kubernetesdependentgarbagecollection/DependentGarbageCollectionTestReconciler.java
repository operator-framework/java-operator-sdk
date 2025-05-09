package io.javaoperatorsdk.operator.dependent.kubernetesdependentgarbagecollection;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@ControllerConfiguration
public class DependentGarbageCollectionTestReconciler
    implements Reconciler<DependentGarbageCollectionTestCustomResource> {

  private KubernetesClient kubernetesClient;
  private volatile boolean errorOccurred = false;

  ConfigMapDependentResource configMapDependent;

  public DependentGarbageCollectionTestReconciler() {
    configMapDependent = new ConfigMapDependentResource();
  }

  @Override
  public List<EventSource<?, DependentGarbageCollectionTestCustomResource>> prepareEventSources(
      EventSourceContext<DependentGarbageCollectionTestCustomResource> context) {
    return EventSourceUtils.dependentEventSources(context, configMapDependent);
  }

  @Override
  public UpdateControl<DependentGarbageCollectionTestCustomResource> reconcile(
      DependentGarbageCollectionTestCustomResource primary,
      Context<DependentGarbageCollectionTestCustomResource> context) {

    if (primary.getSpec().isCreateConfigMap()) {
      configMapDependent.reconcile(primary, context);
    } else {
      configMapDependent.delete(primary, context);
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public ErrorStatusUpdateControl<DependentGarbageCollectionTestCustomResource> updateErrorStatus(
      DependentGarbageCollectionTestCustomResource resource,
      Context<DependentGarbageCollectionTestCustomResource> context,
      Exception e) {
    // this can happen when a namespace is terminated in test
    if (e instanceof KubernetesClientException) {
      return ErrorStatusUpdateControl.noStatusUpdate();
    }
    errorOccurred = true;
    return ErrorStatusUpdateControl.noStatusUpdate();
  }

  public boolean isErrorOccurred() {
    return errorOccurred;
  }

  private static class ConfigMapDependentResource
      extends KubernetesDependentResource<ConfigMap, DependentGarbageCollectionTestCustomResource>
      implements Creator<ConfigMap, DependentGarbageCollectionTestCustomResource>,
          Updater<ConfigMap, DependentGarbageCollectionTestCustomResource>,
          GarbageCollected<DependentGarbageCollectionTestCustomResource> {

    @Override
    protected ConfigMap desired(
        DependentGarbageCollectionTestCustomResource primary,
        Context<DependentGarbageCollectionTestCustomResource> context) {
      ConfigMap configMap = new ConfigMap();
      configMap.setMetadata(
          new ObjectMetaBuilder()
              .withName(primary.getMetadata().getName())
              .withNamespace(primary.getMetadata().getNamespace())
              .build());
      configMap.setData(Map.of("key", "data"));
      return configMap;
    }
  }
}
