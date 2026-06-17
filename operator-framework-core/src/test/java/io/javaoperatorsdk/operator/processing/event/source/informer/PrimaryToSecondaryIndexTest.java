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

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).containsOnly(ResourceID.fromResource(secondary1));
    assertThat(secondaryResources2).containsOnly(ResourceID.fromResource(secondary1));
  }

  @Test
  void indexesAdditionalResources() {
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);
    primaryToSecondaryIndex.onAddOrUpdate(secondary2, null);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1)
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
    assertThat(secondaryResources2)
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
  }

  @Test
  void removingResourceFromIndex() {
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);
    primaryToSecondaryIndex.onAddOrUpdate(secondary2, null);
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

  @Test
  void updateRemovesObsoletePrimaryWhenReferenceNarrows() {
    // initial version references both primaries (default stub)
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);

    // updated version references only primaryID1
    var updated = updatedVersionOf("secondary1");
    when(secondaryToPrimaryMapperMock.toPrimaryResourceIDs(eq(updated)))
        .thenReturn(Set.of(primaryID1));
    primaryToSecondaryIndex.onAddOrUpdate(updated, secondary1);

    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID1))
        .containsOnly(ResourceID.fromResource(secondary1));
    // primaryID2 is no longer referenced, so its (now empty) entry is removed
    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID2)).isEmpty();
  }

  @Test
  void updateMovesSecondaryBetweenPrimaries() {
    // initial version references only primaryID1
    when(secondaryToPrimaryMapperMock.toPrimaryResourceIDs(eq(secondary1)))
        .thenReturn(Set.of(primaryID1));
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);

    // updated version moves the reference to primaryID2
    var updated = updatedVersionOf("secondary1");
    when(secondaryToPrimaryMapperMock.toPrimaryResourceIDs(eq(updated)))
        .thenReturn(Set.of(primaryID2));
    primaryToSecondaryIndex.onAddOrUpdate(updated, secondary1);

    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID1)).isEmpty();
    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID2))
        .containsOnly(ResourceID.fromResource(secondary1));
  }

  @Test
  void updateOnlyRemovesUpdatedSecondaryFromObsoletePrimary() {
    // two secondaries, each referencing both primaries (default stub)
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);
    primaryToSecondaryIndex.onAddOrUpdate(secondary2, null);

    // secondary1 stops referencing primaryID2
    var updated = updatedVersionOf("secondary1");
    when(secondaryToPrimaryMapperMock.toPrimaryResourceIDs(eq(updated)))
        .thenReturn(Set.of(primaryID1));
    primaryToSecondaryIndex.onAddOrUpdate(updated, secondary1);

    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID1))
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
    // primaryID2 is still referenced by secondary2, so only secondary1 is removed from it
    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID2))
        .containsOnly(ResourceID.fromResource(secondary2));
  }

  @Test
  void updateKeepsIndexUnchangedWhenReferencedPrimariesDoNotChange() {
    primaryToSecondaryIndex.onAddOrUpdate(secondary1, null);

    // updated version still references both primaries (default stub applies to it as well)
    var updated = updatedVersionOf("secondary1");
    primaryToSecondaryIndex.onAddOrUpdate(updated, secondary1);

    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID1))
        .containsOnly(ResourceID.fromResource(secondary1));
    assertThat(primaryToSecondaryIndex.getSecondaryResources(primaryID2))
        .containsOnly(ResourceID.fromResource(secondary1));
  }

  ConfigMap secondary(String name) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(name);
    configMap.getMetadata().setNamespace("default");
    return configMap;
  }

  /**
   * Returns a new version of a secondary with the same {@link ResourceID} but a different content,
   * so it represents an updated resource that the mapper mock can be stubbed for independently.
   */
  ConfigMap updatedVersionOf(String name) {
    ConfigMap configMap = secondary(name);
    configMap.getMetadata().setLabels(Map.of("version", "updated"));
    return configMap;
  }
}
