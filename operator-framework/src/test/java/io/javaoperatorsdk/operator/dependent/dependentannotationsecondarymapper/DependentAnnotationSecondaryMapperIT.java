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
package io.javaoperatorsdk.operator.dependent.dependentannotationsecondarymapper;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.processing.event.source.informer.Mappers.DEFAULT_ANNOTATION_FOR_NAME;
import static io.javaoperatorsdk.operator.processing.event.source.informer.Mappers.DEFAULT_ANNOTATION_FOR_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DependentAnnotationSecondaryMapperIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DependentAnnotationSecondaryMapperReconciler.class)
          .build();

  @Test
  void mapsSecondaryByAnnotation() {
    operator.create(testResource());

    var reconciler =
        operator.getReconcilerOfType(DependentAnnotationSecondaryMapperReconciler.class);

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions()).isEqualTo(1));
    var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);

    var annotations = configMap.getMetadata().getAnnotations();

    assertThat(annotations)
        .containsEntry(DEFAULT_ANNOTATION_FOR_NAME, TEST_RESOURCE_NAME)
        .containsEntry(DEFAULT_ANNOTATION_FOR_NAMESPACE, operator.getNamespace());

    assertThat(configMap.getMetadata().getOwnerReferences()).isEmpty();

    configMap.getData().put("additional_data", "data");
    operator.replace(configMap);

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions()).isEqualTo(2));
  }

  DependentAnnotationSecondaryMapperResource testResource() {
    var res = new DependentAnnotationSecondaryMapperResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
