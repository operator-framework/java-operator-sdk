package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class StatefulSetDesiredSanitizerDependentResource
    extends CRUDKubernetesDependentResource<
        StatefulSet, StatefulSetDesiredSanitizerCustomResource> {

  public static volatile Boolean nonMatchedAtLeastOnce;

  @Override
  protected StatefulSet desired(
      StatefulSetDesiredSanitizerCustomResource primary,
      Context<StatefulSetDesiredSanitizerCustomResource> context) {
    var template =
        ReconcilerUtils.loadYaml(
            StatefulSet.class, getClass(), "/io/javaoperatorsdk/operator/statefulset.yaml");
    template.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    return template;
  }

  @Override
  public Result<StatefulSet> match(
      StatefulSet actualResource,
      StatefulSetDesiredSanitizerCustomResource primary,
      Context<StatefulSetDesiredSanitizerCustomResource> context) {
    var res = super.match(actualResource, primary, context);
    if (!res.matched()) {
      nonMatchedAtLeastOnce = true;
    } else if (nonMatchedAtLeastOnce == null) {
      nonMatchedAtLeastOnce = false;
    }
    return res;
  }
}
