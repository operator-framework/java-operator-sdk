package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependent;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;

@KubernetesDependent(labelSelector = "app.kubernetes.io/managed-by=tomcat-operator")
public class DeploymentDependentResource
    extends KubernetesDependentResource<Deployment, Tomcat> {

  public DeploymentDependentResource(KubernetesClient client) {
    super(client);
  }

  @Override
  public Deployment desired(Tomcat tomcat, Context context) {
    Deployment deployment = TomcatReconciler.loadYaml(Deployment.class, "deployment.yaml");
    final ObjectMeta tomcatMetadata = tomcat.getMetadata();
    final String tomcatName = tomcatMetadata.getName();
    deployment = new DeploymentBuilder(deployment)
        .editMetadata()
        .withName(tomcatName)
        .withNamespace(tomcatMetadata.getNamespace())
        .addToLabels("app", tomcatName)
        .addToLabels("app.kubernetes.io/part-of", tomcatName)
        .addToLabels("app.kubernetes.io/managed-by", "tomcat-operator")
        .endMetadata()
        .editSpec()
        .editSelector().addToMatchLabels("app", tomcatName).endSelector()
        .withReplicas(tomcat.getSpec().getReplicas())
        // set tomcat version
        .editTemplate()
        // make sure label selector matches label (which has to be matched by service selector too)
        .editMetadata().addToLabels("app", tomcatName).endMetadata()
        .editSpec()
        .editFirstContainer().withImage(tomcatImage(tomcat)).endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
    return deployment;
  }

  private String tomcatImage(Tomcat tomcat) {
    return "tomcat:" + tomcat.getSpec().getVersion();
  }

  @Override
  public boolean match(Deployment fetched, Deployment target, Context context) {
    return fetched.getSpec().getTemplate().getSpec().getContainers().get(0).getImage()
        .equals(target.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
  }
}
