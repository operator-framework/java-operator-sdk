package io.javaoperatorsdk.operator.dependent.restart;

import org.junit.jupiter.api.*;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Operator restart and state recovery",
    description =
        """
        Tests that an operator can be stopped and restarted while maintaining correct behavior. \
        After restart, the operator should resume processing existing resources without \
        losing track of their state, demonstrating proper state recovery and persistence.
        """)
class OperatorRestartIT {

  private static final Operator operator = new Operator(o -> o.withCloseClientOnStop(false));
  private static final RestartTestReconciler reconciler = new RestartTestReconciler();
  private static int reconcileNumberBeforeStop = 0;

  @BeforeAll
  static void registerReconciler() {
    LocallyRunOperatorExtension.applyCrd(
        RestartTestCustomResource.class, operator.getKubernetesClient());
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
    await()
        .untilAsserted(
            () ->
                assertThat(reconciler.getNumberOfExecutions())
                    .isGreaterThan(reconcileNumberBeforeStop));
  }

  RestartTestCustomResource testCustomResource() {
    RestartTestCustomResource cr = new RestartTestCustomResource();
    cr.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    return cr;
  }
}
