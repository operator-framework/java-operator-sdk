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
package io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentresourcemanaged;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.GenericKubernetesDependentTestBase;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@Sample(
    tldr = "Generic Kubernetes Dependent Resource (Managed)",
    description =
        """
        Demonstrates how to use GenericKubernetesResource as a managed dependent resource. This \
        test shows how to work with generic Kubernetes resources that don't have a specific \
        Java model class, allowing the operator to manage any Kubernetes resource type \
        dynamically.
        """)
public class GenericKubernetesDependentManagedIT
    extends GenericKubernetesDependentTestBase<GenericKubernetesDependentManagedCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new GenericKubernetesDependentManagedReconciler())
          .build();

  @Override
  public LocallyRunOperatorExtension extension() {
    return extension;
  }

  @Override
  public GenericKubernetesDependentManagedCustomResource testResource(String name, String data) {
    var resource = new GenericKubernetesDependentManagedCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(name).build());
    resource.setSpec(new GenericKubernetesDependentSpec());
    resource.getSpec().setValue(INITIAL_DATA);
    return resource;
  }
}
