package io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.TestUtils;

import static io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa.PatchResourceAndStatusNoSSAReconciler.TEST_ANNOTATION;
import static io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa.PatchResourceAndStatusNoSSAReconciler.TEST_ANNOTATION_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PatchResourceAndStatusNoSSAIT {
  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withUseSSAToPatchPrimaryResource(false))
          .withReconciler(PatchResourceAndStatusNoSSAReconciler.class)
          .build();

  @Test
  void updatesSubResourceStatus() {
    PatchResourceAndStatusNoSSACustomResource resource = createTestCustomResource("1");
    operator.create(resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    TestUtils.waitXms(300);

    PatchResourceAndStatusNoSSACustomResource customResource =
        operator.get(
            PatchResourceAndStatusNoSSACustomResource.class, resource.getMetadata().getName());

    assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(1);
    assertThat(customResource.getStatus().getState())
        .isEqualTo(PatchResourceAndStatusNoSSAStatus.State.SUCCESS);
    assertThat(customResource.getMetadata().getAnnotations().get(TEST_ANNOTATION)).isNotNull();
  }

  void awaitStatusUpdated(String name) {
    await("cr status updated")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              PatchResourceAndStatusNoSSACustomResource cr =
                  operator.get(PatchResourceAndStatusNoSSACustomResource.class, name);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getState())
                  .isEqualTo(PatchResourceAndStatusNoSSAStatus.State.SUCCESS);
            });
  }

  public PatchResourceAndStatusNoSSACustomResource createTestCustomResource(String id) {
    PatchResourceAndStatusNoSSACustomResource resource =
        new PatchResourceAndStatusNoSSACustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName("doubleupdateresource-" + id).build());
    resource.getMetadata().setAnnotations(Map.of(TEST_ANNOTATION, TEST_ANNOTATION_VALUE));
    resource.setSpec(new PatchResourceAndStatusNoSSASpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}
