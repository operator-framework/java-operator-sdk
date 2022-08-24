package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.perresourceeventsource.PerResourceEventSourceCustomResource;
import io.javaoperatorsdk.operator.sample.perresourceeventsource.PerResourcePollingEventSourceTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PerResourcePollingEventSourceIT {

  public static final String NAME_1 = "name1";
  public static final String NAME_2 = "name2";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PerResourcePollingEventSourceTestReconciler())
          .build();

  /**
   * This is kinda some test to verify that the implementation of PerResourcePollingEventSource
   * works with the underling mechanisms in event source manager and other parts of the system.
   **/
  @Test
  void fetchedAndReconciledMultipleTimes() {
    operator.create(PerResourceEventSourceCustomResource.class, resource(NAME_1));
    operator.create(PerResourceEventSourceCustomResource.class, resource(NAME_2));

    var reconciler =
        operator.getReconcilerOfType(PerResourcePollingEventSourceTestReconciler.class);
    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions(NAME_1)).isGreaterThan(2);
      assertThat(reconciler.getNumberOfFetchExecution(NAME_1)).isGreaterThan(2);
      assertThat(reconciler.getNumberOfExecutions(NAME_2)).isGreaterThan(2);
      assertThat(reconciler.getNumberOfFetchExecution(NAME_2)).isGreaterThan(2);
    });
  }

  private PerResourceEventSourceCustomResource resource(String name) {
    var res = new PerResourceEventSourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    return res;
  }

}
