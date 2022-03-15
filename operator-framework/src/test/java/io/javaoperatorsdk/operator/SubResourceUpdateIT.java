package io.javaoperatorsdk.operator;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResource;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResourceSpec;
import io.javaoperatorsdk.operator.support.TestUtils;

import static io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResourceStatus.State.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SubResourceUpdateIT {

  public static final int WAIT_AFTER_EXECUTION = 500;
  public static final int EVENT_RECEIVE_WAIT = 200;

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder().withReconciler(SubResourceTestCustomReconciler.class).build();

  @Test
  void updatesSubResourceStatus() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    operator.create(SubResourceTestCustomResource.class, resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(2);
  }

  @Test
  void updatesSubResourceStatusNoFinalizer() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource.getMetadata().setFinalizers(Collections.emptyList());

    operator.create(SubResourceTestCustomResource.class, resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(2);
  }

  /** Note that we check on controller impl if there is finalizer on execution. */
  @Test
  void ifNoFinalizerPresentFirstAddsTheFinalizerThenExecutesControllerAgain() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource.getMetadata().getFinalizers().clear();
    operator.create(SubResourceTestCustomResource.class, resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(2);
  }

  /**
   * Not that here status sub-resource update will fail on optimistic locking. This solves a tricky
   * situation: If this would not happen (no optimistic locking on status sub-resource) we could
   * receive and store an event while processing the controller method. But this event would always
   * fail since its resource version is outdated already.
   */
  @Test
  void updateCustomResourceAfterSubResourceChange() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    operator.create(SubResourceTestCustomResource.class, resource);

    // waits for the resource to start processing
    waitXms(EVENT_RECEIVE_WAIT);
    resource.getSpec().setValue("new value");
    operator.resources(SubResourceTestCustomResource.class).createOrReplace(resource);

    awaitStatusUpdated(resource.getMetadata().getName());

    // wait for sure, there are no more events
    waitXms(WAIT_AFTER_EXECUTION);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(3);
  }

  void awaitStatusUpdated(String name) {
    await("cr status updated")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              SubResourceTestCustomResource cr =
                  operator.get(SubResourceTestCustomResource.class, name);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getState())
                  .isEqualTo(SUCCESS);
            });
  }

  public SubResourceTestCustomResource createTestCustomResource(String id) {
    SubResourceTestCustomResource resource = new SubResourceTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("subresource-" + id)
            .build());
    resource.setKind("SubresourceSample");
    resource.setSpec(new SubResourceTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }

  public static void waitXms(int x) {
    try {
      Thread.sleep(x);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
