package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;

import static io.javaoperatorsdk.operator.sample.Utils.*;

public class IngressDependentResource extends CRUKubernetesDependentResource<Ingress, WebPage> {

  public IngressDependentResource() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(WebPage webPage, Context<WebPage> context) {
    return makeDesiredIngress(webPage);
  };

}
