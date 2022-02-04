package io.javaoperatorsdk.operator.sample;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependent;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@KubernetesDependent(labelSelector = "app.kubernetes.io/managed-by=tomcat-operator")
public class DeploymentDependentResource
    implements DependentResource<Deployment, Tomcat> {

  @Override
  public Optional<Deployment> desired(Tomcat tomcat, Context context) {
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
    return Optional.of(deployment);
  }

  private String tomcatImage(Tomcat tomcat) {
    return "tomcat:" + tomcat.getSpec().getVersion();
  }

  @Override
  public boolean match(Deployment fetched, Tomcat tomcat, Context context) {
    return fetched.getSpec().getTemplate().getSpec().getContainers().stream()
        .findFirst().map(c -> tomcatImage(tomcat).equals(c.getImage())).orElse(false);
  }
}
