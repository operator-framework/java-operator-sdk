package io.javaoperatorsdk.operator.sample.clusterscopedresource;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@ControllerConfiguration
public class ClusterScopedCustomResourceReconciler
    implements Reconciler<ClusterScopedCustomResource>,
    EventSourceInitializer<ClusterScopedCustomResource> {

  public static final String DATA_KEY = "data-key";

  public static final String TEST_LABEL_VALUE = "clusterscopecrtest";
  public static final String TEST_LABEL_KEY = "test";

  @Override
  public UpdateControl<ClusterScopedCustomResource> reconcile(
      ClusterScopedCustomResource resource, Context<ClusterScopedCustomResource> context) {

    var optionalConfigMap = context.getSecondaryResource(ConfigMap.class);

    final var client = context.getClient();
    optionalConfigMap.ifPresentOrElse(cm -> {
      if (!resource.getSpec().getData().equals(cm.getData().get(DATA_KEY))) {
        client.configMaps().resource(desired(resource)).replace();
      }
    }, () -> client.configMaps().resource(desired(resource)).create());

    resource.setStatus(new ClusterScopedCustomResourceStatus());
    resource.getStatus().setCreated(true);
    return UpdateControl.patchStatus(resource);
  }

  private ConfigMap desired(ClusterScopedCustomResource resource) {
    var cm = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getSpec().getTargetNamespace())
            .withLabels(Map.of(TEST_LABEL_KEY, TEST_LABEL_VALUE))
            .build())
        .withData(Map.of(DATA_KEY, resource.getSpec().getData()))
        .build();
    cm.addOwnerReference(resource);
    return cm;
  }

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ClusterScopedCustomResource> context) {
    var ies = new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
        .withSecondaryToPrimaryMapper(Mappers.fromOwnerReference(true))
        .withLabelSelector(TEST_LABEL_KEY + "=" + TEST_LABEL_VALUE)
        .build(), context);
    return EventSourceInitializer.nameEventSources(ies);
  }
}
