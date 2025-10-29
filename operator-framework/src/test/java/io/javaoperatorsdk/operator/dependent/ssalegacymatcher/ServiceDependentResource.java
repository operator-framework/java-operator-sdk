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
package io.javaoperatorsdk.operator.dependent.ssalegacymatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;

@KubernetesDependent
public class ServiceDependentResource
    extends CRUDKubernetesDependentResource<Service, SSALegacyMatcherCustomResource> {

  public static AtomicInteger createUpdateCount = new AtomicInteger(0);

  @Override
  protected Service desired(
      SSALegacyMatcherCustomResource primary, Context<SSALegacyMatcherCustomResource> context) {

    Service service =
        loadYaml(
            Service.class,
            SSAWithLegacyMatcherIT.class,
            "/io/javaoperatorsdk/operator/service.yaml");
    service.getMetadata().setName(primary.getMetadata().getName());
    service.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "deployment-name");
    service.getSpec().setSelector(labels);
    return service;
  }

  @Override
  public Result<Service> match(
      Service actualResource,
      SSALegacyMatcherCustomResource primary,
      Context<SSALegacyMatcherCustomResource> context) {
    var desired = desired(primary, context);

    return GenericKubernetesResourceMatcher.match(
        this, actualResource, primary, context, false, false);
  }

  // override just to check the exec count
  @Override
  public Service update(
      Service actual,
      Service desired,
      SSALegacyMatcherCustomResource primary,
      Context<SSALegacyMatcherCustomResource> context) {
    createUpdateCount.addAndGet(1);
    return super.update(actual, desired, primary, context);
  }

  // override just to check the exec count
  @Override
  public Service create(
      Service desired,
      SSALegacyMatcherCustomResource primary,
      Context<SSALegacyMatcherCustomResource> context) {
    createUpdateCount.addAndGet(1);
    return super.create(desired, primary, context);
  }
}
