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
