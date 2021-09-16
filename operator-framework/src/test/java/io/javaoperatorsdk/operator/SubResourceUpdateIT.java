package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.support.TestUtils;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResource;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResourceController;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.subresource.SubResourceTestCustomResourceStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class SubResourceUpdateIT {

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(SubResourceTestCustomResourceController.class)
          .build();

  @Test
  public void updatesSubResourceStatus() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    operator.resources(SubResourceTestCustomResource.class).create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(200);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(2);
  }

  @Test
  public void updatesSubResourceStatusNoFinalizer() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource.getMetadata().setFinalizers(Collections.emptyList());

    operator.resources(SubResourceTestCustomResource.class).create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(200);
    // there is no event on status update processed
    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(2);
  }

  /** Note that we check on controller impl if there is finalizer on execution. */
  @Test
  public void ifNoFinalizerPresentFirstAddsTheFinalizerThenExecutesControllerAgain() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    resource.getMetadata().getFinalizers().clear();
    operator.resources(SubResourceTestCustomResource.class).create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    waitXms(200);
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
  public void updateCustomResourceAfterSubResourceChange() {
    SubResourceTestCustomResource resource = createTestCustomResource("1");
    operator.resources(SubResourceTestCustomResource.class).create(resource);

    resource.getSpec().setValue("new value");
    operator.resources(SubResourceTestCustomResource.class).createOrReplace(resource);

    awaitStatusUpdated(resource.getMetadata().getName());

    // wait for sure, there are no more events
    waitXms(200);
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
                  operator.resources(SubResourceTestCustomResource.class)
                      .withName(name)
                      .get();
              assertThat(cr.getMetadata().getFinalizers()).hasSize(1);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getState())
                  .isEqualTo(SubResourceTestCustomResourceStatus.State.SUCCESS);
            });
  }

  public SubResourceTestCustomResource createTestCustomResource(String id) {
    SubResourceTestCustomResource resource = new SubResourceTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("subresource-" + id)
            .withFinalizers(SubResourceTestCustomResourceController.FINALIZER_NAME)
            .build());
    resource.setKind("SubresourceSample");
    resource.setSpec(new SubResourceTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }

  private void waitXms(int x) {
    try {
      Thread.sleep(x);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
