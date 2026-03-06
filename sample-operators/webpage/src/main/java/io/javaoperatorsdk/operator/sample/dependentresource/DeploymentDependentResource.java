/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample.dependentresource;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.sample.Utils;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.configMapName;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class DeploymentDependentResource
    extends CRUDKubernetesDependentResource<Deployment, WebPage> {

  @Override
  protected Deployment desired(WebPage webPage, Context<WebPage> context) {
    Map<String, String> labels = new HashMap<>();
    labels.put(SELECTOR, "true");
    var deploymentName = deploymentName(webPage);
    Deployment deployment = loadYaml(Deployment.class, Utils.class, "deployment.yaml");
    deployment.getMetadata().setName(deploymentName);
    deployment.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    deployment.getMetadata().setLabels(labels);
    deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);

    deployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName);
    deployment
        .getSpec()
        .getTemplate()
        .getSpec()
        .getVolumes()
        .get(0)
        .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName(webPage)).build());

    return deployment;
  }
}
