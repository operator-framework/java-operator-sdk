package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class RouteDependentResource
    extends CRUDKubernetesDependentResource<Route, GetNonActiveSecondaryCustomResource> {

  @Override
  protected Route desired(
      GetNonActiveSecondaryCustomResource primary,
      Context<GetNonActiveSecondaryCustomResource> context) {
    // basically does not matter since this should not be called
    Route route = new Route();
    route.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());

    return route;
  }
}
