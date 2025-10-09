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
package io.javaoperatorsdk.operator.dependent.dependentdifferentnamespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.dependentdifferentnamespace.ConfigMapDependentResource.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Dependent Resources in Different Namespaces",
    description =
        """
        Demonstrates how to manage dependent resources in a namespace different from the primary \
        resource. This test shows how to configure dependent resources to be created in a \
        specific namespace rather than inheriting the namespace from the primary resource. \
        The test verifies full CRUD operations for a ConfigMap that lives in a different \
        namespace than the custom resource that manages it.
        """)
class DependentDifferentNamespaceIT {

  public static final String TEST_1 = "different-ns-test1";
  public static final String INITIAL_VALUE = "initial_value";
  public static final String CHANGED_VALUE = "changed_value";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DependentDifferentNamespaceReconciler.class)
          .build();

  @Test
  void managesCRUDOperationsForDependentInDifferentNamespace() {
    var resource = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm).isNotNull();
              assertThat(cm.getData()).containsEntry(KEY, INITIAL_VALUE);
            });

    resource.getSpec().setValue(CHANGED_VALUE);
    resource = extension.replace(resource);

    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm.getData()).containsEntry(KEY, CHANGED_VALUE);
            });

    extension.delete(resource);
    await()
        .untilAsserted(
            () -> {
              var cm = getDependentConfigMap();
              assertThat(cm).isNull();
            });
  }

  private ConfigMap getDependentConfigMap() {
    return extension
        .getKubernetesClient()
        .configMaps()
        .inNamespace(ConfigMapDependentResource.NAMESPACE)
        .withName(TEST_1)
        .get();
  }

  DependentDifferentNamespaceCustomResource testResource() {
    var res = new DependentDifferentNamespaceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new DependentDifferentNamespaceSpec());
    res.getSpec().setValue(INITIAL_VALUE);
    return res;
  }
}
