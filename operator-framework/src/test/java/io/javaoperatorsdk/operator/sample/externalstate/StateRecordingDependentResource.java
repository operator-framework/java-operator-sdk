package io.javaoperatorsdk.operator.sample.externalstate;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.support.ExternalResource;

import static io.javaoperatorsdk.operator.sample.externalstate.ExternalStateReconciler.ID_KEY;

public class StateRecordingDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, ExternalStateCustomResource> {

  public StateRecordingDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    final var external = context.getSecondaryResource(ExternalResource.class).orElseThrow();
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of(ID_KEY, external.getId()))
        .build();
  }
}
