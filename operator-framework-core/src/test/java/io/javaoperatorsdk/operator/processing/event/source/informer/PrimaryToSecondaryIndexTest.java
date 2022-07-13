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

  private SecondaryToPrimaryMapper<ConfigMap> secondaryToPrimaryMapperMock =
      mock(SecondaryToPrimaryMapper.class);
  private PrimaryToSecondaryIndex<ConfigMap> primaryToSecondaryIndex =
      new DefaultPrimaryToSecondaryIndex<>(secondaryToPrimaryMapperMock);

  private ResourceID primaryID1 = new ResourceID("id1", "default");
  private ResourceID primaryID2 = new ResourceID("id2", "default");
  private ConfigMap secondary1 = secondary("secondary1");
  private ConfigMap secondary2 = secondary("secondary2");

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

    assertThat(secondaryResources1).containsOnly(ResourceID.fromResource(secondary1),
        ResourceID.fromResource(secondary2));
    assertThat(secondaryResources2).containsOnly(ResourceID.fromResource(secondary1),
        ResourceID.fromResource(secondary2));
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
