package io.javaoperatorsdk.operator.sample;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(labelSelector = SELECTOR)
public class DeploymentDependentResource
    extends CRUDKubernetesDependentResource<Deployment, WebPage> {

  public DeploymentDependentResource() {
    super(Deployment.class);
  }

  @Override
  protected Deployment desired(WebPage webPage, Context<WebPage> context) {
    Map<String, String> labels = new HashMap<>();
    labels.put(SELECTOR, "true");
    var deploymentName = deploymentName(webPage);
    Deployment deployment = loadYaml(Deployment.class, getClass(), "deployment.yaml");
    deployment.getMetadata().setName(deploymentName);
    deployment.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    deployment.getMetadata().setLabels(labels);
    deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);

    deployment
        .getSpec()
        .getTemplate()
        .getMetadata()
        .getLabels()
        .put("app", deploymentName);
    deployment
        .getSpec()
        .getTemplate()
        .getSpec()
        .getVolumes()
        .get(0)
        .setConfigMap(
            new ConfigMapVolumeSourceBuilder().withName(configMapName(webPage)).build());

    return deployment;
  }
}
