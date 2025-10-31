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

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.sample.Utils;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.Utils.serviceName;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class ServiceDependentResource
    extends io.javaoperatorsdk.operator.processing.dependent.kubernetes
            .CRUDKubernetesDependentResource<
        Service, WebPage> {

  @Override
  protected Service desired(WebPage webPage, Context<WebPage> context) {
    Map<String, String> serviceLabels = new HashMap<>();
    serviceLabels.put(SELECTOR, "true");
    Service service = loadYaml(Service.class, Utils.class, "service.yaml");
    service.getMetadata().setName(serviceName(webPage));
    service.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    service.getMetadata().setLabels(serviceLabels);
    Map<String, String> labels = new HashMap<>();
    labels.put("app", deploymentName(webPage));
    service.getSpec().setSelector(labels);
    return service;
  }
}
