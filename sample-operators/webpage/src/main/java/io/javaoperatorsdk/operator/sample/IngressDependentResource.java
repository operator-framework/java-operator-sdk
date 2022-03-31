package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.*;

public class IngressDependentResource extends CRUKubernetesDependentResource<Ingress, WebPage> {

  public IngressDependentResource() {
    super(Ingress.class);
  }

  @Override
  protected Ingress desired(WebPage webPage, Context<WebPage> context) {
    Ingress ingress = loadYaml(Ingress.class, getClass(), "ingress.yaml");
    ingress.getMetadata().setName(webPage.getMetadata().getName());
    ingress.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0)
        .getBackend().getService().setName(serviceName(webPage));
    return ingress;
  };


}
