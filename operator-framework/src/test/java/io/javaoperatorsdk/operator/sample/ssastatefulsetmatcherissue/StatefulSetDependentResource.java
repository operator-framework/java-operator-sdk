package io.javaoperatorsdk.operator.sample.ssastatefulsetmatcherissue;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class StatefulSetDependentResource
    extends CRUDKubernetesDependentResource<StatefulSet, SSAStatefulSetMatcherIssueCustomResource> {

  public StatefulSetDependentResource() {

    super(StatefulSet.class);
  }

  @Override
  protected StatefulSet desired(SSAStatefulSetMatcherIssueCustomResource primary,
      Context<SSAStatefulSetMatcherIssueCustomResource> context) {
    var template =
        ReconcilerUtils.loadYaml(StatefulSet.class, getClass(), "statefulset_fixed_full.yaml");
    template.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName())
        .withNamespace(primary.getMetadata().getNamespace())
        .build());
    return template;
  }
}
