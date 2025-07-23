package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultComplementaryPrimaryToSecondaryIndexTest {

  @SuppressWarnings("unchecked")
  private final SecondaryToPrimaryMapper<ConfigMap> secondaryToPrimaryMapperMock =
      mock(SecondaryToPrimaryMapper.class);

  private final DefaultComplementaryPrimaryToSecondaryIndex<ConfigMap> primaryToSecondaryIndex =
      new DefaultComplementaryPrimaryToSecondaryIndex<>(secondaryToPrimaryMapperMock);

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
    primaryToSecondaryIndex.explicitAddOrUpdate(secondary1);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).containsOnly(ResourceID.fromResource(secondary1));
    assertThat(secondaryResources2).containsOnly(ResourceID.fromResource(secondary1));
  }

  @Test
  void indexesAdditionalResources() {
    primaryToSecondaryIndex.explicitAddOrUpdate(secondary1);
    primaryToSecondaryIndex.explicitAddOrUpdate(secondary2);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1)
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
    assertThat(secondaryResources2)
        .containsOnly(ResourceID.fromResource(secondary1), ResourceID.fromResource(secondary2));
  }

  @Test
  void removingResourceFromIndex() {
    primaryToSecondaryIndex.explicitAddOrUpdate(secondary1);
    primaryToSecondaryIndex.explicitAddOrUpdate(secondary2);
    primaryToSecondaryIndex.cleanupForResource(secondary1);

    var secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    var secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).containsOnly(ResourceID.fromResource(secondary2));
    assertThat(secondaryResources2).containsOnly(ResourceID.fromResource(secondary2));

    primaryToSecondaryIndex.cleanupForResource(secondary2);

    secondaryResources1 = primaryToSecondaryIndex.getSecondaryResources(primaryID1);
    secondaryResources2 = primaryToSecondaryIndex.getSecondaryResources(primaryID2);

    assertThat(secondaryResources1).isEmpty();
    assertThat(secondaryResources2).isEmpty();
  }

  @Test
  void testPerformance() {
    var primaryToSecondaryIndex =
        new DefaultComplementaryPrimaryToSecondaryIndex<>(
            new SecondaryToPrimaryMapper<HasMetadata>() {
              @Override
              public Set<ResourceID> toPrimaryResourceIDs(HasMetadata resource) {
                return Set.of(
                    new ResourceID(
                        resource.getMetadata().getName(), resource.getMetadata().getNamespace()));
              }
            });
    var start = LocalDateTime.now();
    for (int i = 0; i < 1_000_000; i++) {
      primaryToSecondaryIndex.explicitAddOrUpdate(cm(i));
    }
    System.out.println(ChronoUnit.MILLIS.between(start, LocalDateTime.now()));

    start = LocalDateTime.now();
    for (int i = 0; i < 1_000_000; i++) {
      primaryToSecondaryIndex.cleanupForResource(cm(i));
    }
    System.out.println(ChronoUnit.MILLIS.between(start, LocalDateTime.now()));
    System.out.println("ok");
  }

  private static ConfigMap cm(int i) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder().withName("test" + i).withNamespace("default").build())
        .build();
  }

  ConfigMap secondary(String name) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(name);
    configMap.getMetadata().setNamespace("default");
    return configMap;
  }
}
