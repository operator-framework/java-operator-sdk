package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(
    informer = @Informer(labelSelector = "app.kubernetes.io/managed-by=tomcat-operator"))
public class DeploymentDependentResource
    extends CRUDKubernetesDependentResource<Deployment, Tomcat> {

  private static String tomcatImage(Tomcat tomcat) {
    return "tomcat:" + tomcat.getSpec().getVersion();
  }

  @Override
  protected Deployment desired(Tomcat tomcat, Context<Tomcat> context) {
    Deployment deployment =
        ReconcilerUtils.loadYaml(Deployment.class, getClass(), "deployment.yaml");
    final ObjectMeta tomcatMetadata = tomcat.getMetadata();
    final String tomcatName = tomcatMetadata.getName();
    deployment =
        new DeploymentBuilder(deployment)
            .editMetadata()
            .withName(tomcatName)
            .withNamespace(tomcatMetadata.getNamespace())
            .addToLabels("app", tomcatName)
            .addToLabels("app.kubernetes.io/part-of", tomcatName)
            .addToLabels("app.kubernetes.io/managed-by", "tomcat-operator")
            .endMetadata()
            .editSpec()
            .editSelector()
            .addToMatchLabels("app", tomcatName)
            .endSelector()
            .withReplicas(tomcat.getSpec().getReplicas())
            // set tomcat version
            .editTemplate()
            // make sure label selector matches label (which has to be matched by service selector
            // too)
            .editMetadata()
            .addToLabels("app", tomcatName)
            .endMetadata()
            .editSpec()
            .editFirstContainer()
            .withImage(tomcatImage(tomcat))
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();
    return deployment;
  }
}
