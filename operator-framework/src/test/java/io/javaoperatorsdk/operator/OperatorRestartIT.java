package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.restart.RestartTestCustomResource;
import io.javaoperatorsdk.operator.sample.restart.RestartTestReconciler;
import org.junit.jupiter.api.*;

class OperatorRestartIT {

  private final static Operator operator = new Operator(o -> o.withCloseClientOnStop(false));
  private final static RestartTestReconciler reconciler = new RestartTestReconciler();
  private static int reconcileNumberBeforeStop = 0;

  @BeforeAll
  static void registerReconciler() {
    LocallyRunOperatorExtension.applyCrd(RestartTestCustomResource.class,
        operator.getKubernetesClient());
    operator.register(reconciler);
  }

  @BeforeEach
  void startOperator() {
    operator.start();
  }

  @AfterEach
  void stopOperator() {
    operator.stop();
  }

  @Test
  @Order(1)
  void createResource() {
    operator.getKubernetesClient().resource(testCustomResource()).createOrReplace();
    await().untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0));
    reconcileNumberBeforeStop = reconciler.getNumberOfExecutions();
  }

  @Test
  @Order(2)
  void reconcile() {
    await().untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions())
        .isGreaterThan(reconcileNumberBeforeStop));
  }

  RestartTestCustomResource testCustomResource() {
    RestartTestCustomResource cr = new RestartTestCustomResource();
    cr.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    return cr;
  }
}
