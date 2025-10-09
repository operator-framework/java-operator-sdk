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
package io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation.CustomMappingConfigMapDependentResource.CUSTOM_NAMESPACE_KEY;
import static io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation.CustomMappingConfigMapDependentResource.CUSTOM_NAME_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Custom Annotation Keys for Resource Mapping",
    description =
        """
        Tests custom annotation-based mapping for dependent resources using configurable \
        annotation keys instead of the default ones. This allows developers to customize which \
        annotations are used to establish relationships between primary and secondary resources, \
        providing flexibility for different naming conventions or avoiding conflicts.
        """)
class DependentCustomMappingAnnotationIT {

  public static final String INITIAL_VALUE = "initial value";
  public static final String CHANGED_VALUE = "changed value";
  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DependentCustomMappingReconciler.class)
          .build();

  @Test
  void testCustomMappingAnnotationForDependent() {
    var cr = extension.create(testResource());
    assertConfigMapData(INITIAL_VALUE);

    cr.getSpec().setValue(CHANGED_VALUE);
    cr = extension.replace(cr);
    assertConfigMapData(CHANGED_VALUE);

    extension.delete(cr);

    await()
        .untilAsserted(
            () -> {
              var resource = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(resource).isNull();
            });
  }

  private void assertConfigMapData(String val) {
    await()
        .untilAsserted(
            () -> {
              var resource = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(resource).isNotNull();
              assertThat(resource.getMetadata().getAnnotations())
                  .containsKey(CUSTOM_NAME_KEY)
                  .containsKey(CUSTOM_NAMESPACE_KEY);
              assertThat(resource.getData())
                  .containsEntry(CustomMappingConfigMapDependentResource.KEY, val);
            });
  }

  DependentCustomMappingCustomResource testResource() {
    var dr = new DependentCustomMappingCustomResource();
    dr.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    dr.setSpec(new DependentCustomMappingSpec());
    dr.getSpec().setValue(INITIAL_VALUE);

    return dr;
  }
}
