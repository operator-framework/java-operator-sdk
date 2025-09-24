package io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SSAFinalizerIssueIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SSAFinalizerIssueReconciler())
          .build();

  /**
   * Showcases possibly a case with SSA: When the resource is created with the same field manager
   * that used by the controller, when adding a finalizer, it deletes other parts of the spec.
   */
  @Test
  void addingFinalizerRemoveListValues() {
    var fieldManager =
        operator
            .getRegisteredControllerForReconcile(SSAFinalizerIssueReconciler.class)
            .getConfiguration()
            .fieldManager();

    operator
        .getKubernetesClient()
        .resource(testResource())
        .inNamespace(operator.getNamespace())
        .patch(
            new PatchContext.Builder()
                .withFieldManager(fieldManager)
                .withForce(true)
                .withPatchType(PatchType.SERVER_SIDE_APPLY)
                .build());

    await()
        .untilAsserted(
            () -> {
              var actual = operator.get(SSAFinalizerIssueCustomResource.class, TEST_1);
              assertThat(actual.getFinalizers()).hasSize(1);
              assertThat(actual.getSpec()).isNull();
            });
  }

  SSAFinalizerIssueCustomResource testResource() {
    var res = new SSAFinalizerIssueCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new SSAFinalizerIssueSpec());
    res.getSpec().setValue("val");
    res.getSpec().setList(List.of("val1", "val2"));
    return res;
  }
}
