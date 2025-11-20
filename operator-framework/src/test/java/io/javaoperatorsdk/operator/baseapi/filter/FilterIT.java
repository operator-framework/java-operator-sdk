package io.javaoperatorsdk.operator.baseapi.filter;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.filter.FilterTestReconciler.CONFIG_MAP_FILTER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Filtering Events for Primary and Secondary Resources",
    description =
        "Demonstrates how to implement event filters for both primary custom resources and"
            + " secondary dependent resources. The test verifies that resource updates matching"
            + " specific filter criteria are ignored and don't trigger reconciliation. This helps"
            + " reduce unnecessary reconciliation executions and improve operator efficiency.")
class FilterIT {

  public static final String RESOURCE_NAME = "test1";
  public static final int POLL_DELAY = 150;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(FilterTestReconciler.class).build();

  @Test
  void filtersControllerResourceUpdate() {
    var res = operator.create(createResource());
    // One for CR create event other for ConfigMap event
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));

    res.getSpec().setValue(FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE);
    operator.replace(res);

    // not more reconciliation with the filtered value
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));
  }

  @Test
  void filtersSecondaryResourceUpdate() {
    var res = operator.create(createResource());
    // One for CR create event other for ConfigMap event
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));

    res.getSpec().setValue(CONFIG_MAP_FILTER_VALUE);
    operator.replace(res);

    // the CM event filtered out
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(3));
  }

  FilterTestCustomResource createResource() {
    FilterTestCustomResource resource = new FilterTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource.setSpec(new FilterTestResourceSpec());
    resource.getSpec().setValue("value1");
    return resource;
  }
}
