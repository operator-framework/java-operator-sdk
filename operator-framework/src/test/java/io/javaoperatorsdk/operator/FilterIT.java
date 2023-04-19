package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.filter.FilterTestCustomResource;
import io.javaoperatorsdk.operator.sample.filter.FilterTestReconciler;
import io.javaoperatorsdk.operator.sample.filter.FilterTestResourceSpec;

import static io.javaoperatorsdk.operator.sample.filter.FilterTestReconciler.CONFIG_MAP_FILTER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnableKubeAPIServer
class FilterIT {

  public static final String RESOURCE_NAME = "test1";
  public static final int POLL_DELAY = 150;

  static KubernetesClient client;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withKubernetesClient(client)
          .waitForNamespaceDeletion(false)
          .withReconciler(FilterTestReconciler.class)
          .build();

  @Test
  void filtersControllerResourceUpdate() {
    var res = operator.create(createResource());
    // One for CR create event other for ConfigMap event
    await().pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(operator.getReconcilerOfType(FilterTestReconciler.class)
            .getNumberOfExecutions()).isEqualTo(2));

    res.getSpec().setValue(FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE);
    operator.replace(res);

    // not more reconciliation with the filtered value
    await().pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(operator.getReconcilerOfType(FilterTestReconciler.class)
            .getNumberOfExecutions()).isEqualTo(2));
  }

  @Test
  void filtersSecondaryResourceUpdate() {
    var res = operator.create(createResource());
    // One for CR create event other for ConfigMap event
    await().pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(operator.getReconcilerOfType(FilterTestReconciler.class)
            .getNumberOfExecutions()).isEqualTo(2));

    res.getSpec().setValue(CONFIG_MAP_FILTER_VALUE);
    operator.replace(res);

    // the CM event filtered out
    await().pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(operator.getReconcilerOfType(FilterTestReconciler.class)
            .getNumberOfExecutions()).isEqualTo(3));
  }


  FilterTestCustomResource createResource() {
    FilterTestCustomResource resource = new FilterTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(RESOURCE_NAME)
        .build());
    resource.setSpec(new FilterTestResourceSpec());
    resource.getSpec().setValue("value1");
    return resource;
  }

}
