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
package io.javaoperatorsdk.operator.dependent.servicestrictmatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;

@KubernetesDependent
public class ServiceDependentResource
    extends CRUDKubernetesDependentResource<Service, ServiceStrictMatcherTestCustomResource> {

  public static AtomicInteger updated = new AtomicInteger(0);

  @Override
  protected Service desired(
      ServiceStrictMatcherTestCustomResource primary,
      Context<ServiceStrictMatcherTestCustomResource> context) {
    Service service =
        loadYaml(
            Service.class,
            ServiceStrictMatcherIT.class,
            "/io/javaoperatorsdk/operator/service.yaml");
    service.getMetadata().setName(primary.getMetadata().getName());
    service.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "deployment-name");
    service.getSpec().setSelector(labels);
    return service;
  }

  @Override
  public Matcher.Result<Service> match(
      Service actualResource,
      ServiceStrictMatcherTestCustomResource primary,
      Context<ServiceStrictMatcherTestCustomResource> context) {
    return GenericKubernetesResourceMatcher.match(
        this,
        actualResource,
        primary,
        context,
        false,
        false,
        "/spec/ports",
        "/spec/clusterIP",
        "/spec/clusterIPs",
        "/spec/externalTrafficPolicy",
        "/spec/internalTrafficPolicy",
        "/spec/ipFamilies",
        "/spec/ipFamilyPolicy",
        "/spec/sessionAffinity");
  }

  @Override
  public Service update(
      Service actual,
      Service desired,
      ServiceStrictMatcherTestCustomResource primary,
      Context<ServiceStrictMatcherTestCustomResource> context) {
    updated.addAndGet(1);
    return super.update(actual, desired, primary, context);
  }
}
