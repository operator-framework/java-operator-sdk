package io.javaoperatorsdk.operator;

import static io.javaoperatorsdk.operator.IntegrationTestSupport.TEST_NAMESPACE;
import static io.javaoperatorsdk.operator.TestUtils.waitXms;
import static io.javaoperatorsdk.operator.doubleupdate.DoubleUpdateTestCustomResourceController.TEST_ANNOTATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.doubleupdate.DoubleUpdateTestCustomResource;
import io.javaoperatorsdk.operator.doubleupdate.DoubleUpdateTestCustomResourceController;
import io.javaoperatorsdk.operator.doubleupdate.DoubleUpdateTestCustomResourceSpec;
import io.javaoperatorsdk.operator.doubleupdate.DoubleUpdateTestCustomResourceStatus;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UpdatingResAndSubResIT {

  private IntegrationTestSupport integrationTestSupport = new IntegrationTestSupport();

  @BeforeEach
  public void initAndCleanup() {
    KubernetesClient k8sClient = new DefaultKubernetesClient();
    integrationTestSupport.initialize(
        k8sClient, new DoubleUpdateTestCustomResourceController(), "doubleupdate-test-crd.yaml");
    integrationTestSupport.cleanup();
  }

  @Test
  public void updatesSubResourceStatus() {
    integrationTestSupport.teardownIfSuccess(
        () -> {
          DoubleUpdateTestCustomResource resource = createTestCustomResource("1");
          integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).create(resource);

          awaitStatusUpdated(resource.getMetadata().getName());
          // wait for sure, there are no more events
          waitXms(300);

          DoubleUpdateTestCustomResource customResource =
              (DoubleUpdateTestCustomResource)
                  integrationTestSupport.getCustomResource(resource.getMetadata().getName());
          assertThat(integrationTestSupport.numberOfControllerExecutions()).isEqualTo(1);
          assertThat(customResource.getStatus().getState())
              .isEqualTo(DoubleUpdateTestCustomResourceStatus.State.SUCCESS);
          assertThat(customResource.getMetadata().getAnnotations().get(TEST_ANNOTATION))
              .isNotNull();
        });
  }

  void awaitStatusUpdated(String name) {
    await("cr status updated")
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              DoubleUpdateTestCustomResource cr =
                  (DoubleUpdateTestCustomResource)
                      integrationTestSupport
                          .getCrOperations()
                          .inNamespace(TEST_NAMESPACE)
                          .withName(name)
                          .get();
              assertThat(cr.getMetadata().getFinalizers()).hasSize(1);
              assertThat(cr).isNotNull();
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getState())
                  .isEqualTo(DoubleUpdateTestCustomResourceStatus.State.SUCCESS);
            });
  }

  public DoubleUpdateTestCustomResource createTestCustomResource(String id) {
    DoubleUpdateTestCustomResource resource = new DoubleUpdateTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("doubleupdateresource-" + id)
            .withNamespace(TEST_NAMESPACE)
            .build());
    resource.setKind("DoubleUpdateSample");
    resource.setSpec(new DoubleUpdateTestCustomResourceSpec());
    resource.getSpec().setValue(id);
    return resource;
  }
}
