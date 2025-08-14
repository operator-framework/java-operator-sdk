package io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
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
   * Showcases the problem when we add a finalizer and in the spec a list is initialized with an
   * empty list by default. This at the end results in a change that when adding the finalizer the
   * SSA patch deletes the initial values in the spec.
   */
  @Test
  void addingFinalizerRemoveListValues() {
    operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var actual = operator.get(SSAFinalizerIssueCustomResource.class, TEST_1);
              assertThat(actual.getFinalizers()).hasSize(1);
              assertThat(actual.getSpec().getList()).isEmpty();
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
