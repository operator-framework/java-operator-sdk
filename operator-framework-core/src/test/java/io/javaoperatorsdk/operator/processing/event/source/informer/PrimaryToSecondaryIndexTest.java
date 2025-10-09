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

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrimaryToSecondaryIndexTest {

  @SuppressWarnings("unchecked")
  private final SecondaryToPrimaryMapper<ConfigMap> secondaryToPrimaryMapperMock =
      mock(SecondaryToPrimaryMapper.class);

  private final PrimaryToSecondaryIndex<ConfigMap> primaryToSecondaryIndex =
      new DefaultPrimaryToSecondaryIndex<>(secondaryToPrimaryMapperMock);

  private final ResourceID primaryID1 = new ResourceID("id1", "default");
  private final ResourceID primaryID2 = new ResourceID("id2", "default");
  private final ConfigMap secondary1 = secondary("secondary1");
  private final ConfigMap secondary2 = secondary("secondary2");

  @BeforeEach
  void setup() {
    when(secondaryToPrimaryMapperMock.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(primaryID1, primaryID2));
  }

  @Test
  void returnsEmptySetOnEmptyIndex() {
    var res = primaryToSecondaryIndex.getSecondaryResources(ResourceID.fromResource(secondary1));
    assertThat(res).isEmpty();
  }

  @Test
  void indexesNewResources() {
    primaryToSecondaryIndex.onAddOrUpdate(secondary1);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).containsOnly(ResourceID.fromResource(secondary1));
    assertThat(secondaryResources2).containsOnly(ResourceID.fromResource(secondary1));
  }

  @Test
  void indexesAdditionalResources() {
    primaryToSecondaryIndex.onAddOrUpdate(secondary1);
    primaryToSecondaryIndex.onAddOrUpdate(secondary2);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1)
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
    assertThat(secondaryResources2)
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
  }

  @Test
  void removingResourceFromIndex() {
    primaryToSecondaryIndex.onAddOrUpdate(secondary1);
    primaryToSecondaryIndex.onAddOrUpdate(secondary2);
    primaryToSecondaryIndex.onDelete(secondary1);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).containsOnly(ResourceID.fromResource(secondary2));
    assertThat(secondaryResources2).containsOnly(ResourceID.fromResource(secondary2));

    primaryToSecondaryIndex.onDelete(secondary2);

    secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).isEmpty();
    assertThat(secondaryResources2).isEmpty();
  }

  ConfigMap secondary(String name) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(name);
    configMap.getMetadata().setNamespace("default");
    return configMap;
  }
}
