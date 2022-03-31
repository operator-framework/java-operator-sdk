package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;

public class IngressDependentResource extends CRUKubernetesDependentResource<Ingress, WebPage> {

    public IngressDependentResource() {
        super(Ingress.class);
    }

    @Override
    protected Ingress desired(WebPage webPage, Context<WebPage> context) {
      return null;
    };


}
