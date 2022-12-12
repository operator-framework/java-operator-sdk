package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.cacheprune.CachePruneCustomResource;
import io.javaoperatorsdk.operator.sample.cacheprune.CachePruneReconciler;
import io.javaoperatorsdk.operator.sample.cacheprune.CachePruneSpec;

import static io.javaoperatorsdk.operator.sample.cacheprune.CachePruneReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CachePruneIT {

  public static final String DEFAULT_DATA = "default_data";
  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String UPDATED_DATA = "updated_data";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CachePruneReconciler()).build();

  @Test
  void pruningRelatedBehavior() {
    var res = operator.create(testResource());
    await().untilAsserted(() -> {
      assertState(DEFAULT_DATA);
    });

    res.getSpec().setData(UPDATED_DATA);
    var updated = operator.replace(res);

    await().untilAsserted(() -> {
      assertState(UPDATED_DATA);
    });

    operator.delete(updated);

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      var actual = operator.get(CachePruneCustomResource.class, TEST_RESOURCE_NAME);
      var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(configMap).isNull();
      assertThat(actual).isNull();
    });
  }

  void assertState(String expectedData) {
    var actual = operator.get(CachePruneCustomResource.class, TEST_RESOURCE_NAME);
    assertThat(actual.getMetadata()).isNotNull();
    assertThat(actual.getMetadata().getFinalizers()).isNotEmpty();
    assertThat(actual.getStatus().getCreated()).isTrue();
    assertThat(actual.getMetadata().getLabels()).isNotEmpty();
    var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
    assertThat(configMap.getData()).containsEntry(DATA_KEY, expectedData);
    assertThat(configMap.getMetadata().getLabels()).isNotEmpty();
  }

  CachePruneCustomResource testResource() {
    var res = new CachePruneCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_RESOURCE_NAME)
        .withLabels(Map.of("sampleLabel", "val"))
        .build());
    res.setSpec(new CachePruneSpec());
    res.getSpec().setData(DEFAULT_DATA);
    return res;
  }

}
