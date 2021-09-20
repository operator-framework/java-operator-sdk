package io.javaoperatorsdk.operator;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.doubleupdate.DoubleUpdateTestCustomResource;
import io.javaoperatorsdk.operator.sample.doubleupdate.DoubleUpdateTestCustomResourceController;
import io.javaoperatorsdk.operator.sample.doubleupdate.DoubleUpdateTestCustomResourceSpec;
import io.javaoperatorsdk.operator.sample.doubleupdate.DoubleUpdateTestCustomResourceStatus;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class UpdatingResAndSubResIT {
  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(DoubleUpdateTestCustomResourceController.class)
          .build();

  @Test
  public void updatesSubResourceStatus() {
    DoubleUpdateTestCustomResource resource = createTestCustomResource("1");
    operator.create(DoubleUpdateTestCustomResource.class, resource);

    awaitStatusUpdated(resource.getMetadata().getName());
    // wait for sure, there are no more events
    TestUtils.waitXms(300);

    DoubleUpdateTestCustomResource customResource =
        operator
            .getNamedResource(DoubleUpdateTestCustomResource.class,
                resource.getMetadata().getName());

    assertThat(TestUtils.getNumberOfExecutions(operator))
        .isEqualTo(1);
    assertThat(customResource.getStatus().getState())
        .isEqualTo(DoubleUpdateTestCustomResourceStatus.State.SUCCESS);
    assertThat(
        customResource
            .getMetadata()
            .getAnnotations()
            .get(DoubleUpdateTestCustomResourceController.TEST_ANNOTATION))
                .isNotNull();
  }

  void awaitStatusUpdated(String name) {
    await("cr status updated")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              DoubleUpdateTestCustomResource cr =
                  operator.getNamedResource(DoubleUpdateTestCustomResource.class, name);

              assertThat(cr)
                  .isNotNull();
              assertThat(cr.getMetadata().getFinalizers())
                  .hasSize(1);
              assertThat(cr.getStatus())
                  .isNotNull();
              assertThat(cr.getStatus().getState())
                  .isEqualTo(DoubleUpdateTestCustomResourceStatus.State.SUCCESS);
            });
  }

  public DoubleUpdateTestCustomResource createTestCustomResource(String id) {
    DoubleUpdateTestCustomResource resource = new DoubleUpdateTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName("doubleupdateresource-" + id).build());
    resource.setKind("DoubleUpdateSample");
    resource.setSpec(new DoubleUpdateTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}
