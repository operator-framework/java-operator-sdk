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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceOtherV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MappersTest {

  @Test
  void secondaryToPrimaryMapperFromOwnerReference() {
    var primary = TestUtils.testCustomResource();
    primary.getMetadata().setUid(UUID.randomUUID().toString());
    var secondary = getConfigMap(primary);
    secondary.addOwnerReference(primary);

    var res = Mappers.fromOwnerReferences(TestCustomResource.class).toPrimaryResourceIDs(secondary);

    assertThat(res).contains(ResourceID.fromResource(primary));
  }

  @Test
  void secondaryToPrimaryMapperFromOwnerReferenceWhereGroupIdIsEmpty() {
    var primary =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("test")
            .withNamespace("default")
            .endMetadata()
            .build();
    primary.getMetadata().setUid(UUID.randomUUID().toString());
    var secondary =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName("test1")
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .build();
    secondary.addOwnerReference(primary);

    var res = Mappers.fromOwnerReferences(ConfigMap.class).toPrimaryResourceIDs(secondary);

    assertThat(res).contains(ResourceID.fromResource(primary));
  }

  @Test
  void secondaryToPrimaryMapperFromOwnerReferenceFiltersByType() {
    var primary = TestUtils.testCustomResource();
    primary.getMetadata().setUid(UUID.randomUUID().toString());
    var secondary = getConfigMap(primary);
    secondary.addOwnerReference(primary);

    var res =
        Mappers.fromOwnerReferences(TestCustomResourceOtherV1.class)
            .toPrimaryResourceIDs(secondary);

    assertThat(res).isEmpty();
  }

  private static ConfigMap getConfigMap(TestCustomResource primary) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName("test1")
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .build();
  }
}
