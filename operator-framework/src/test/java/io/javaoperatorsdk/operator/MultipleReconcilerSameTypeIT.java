package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.multiplereconcilersametype.MultipleReconcilerSameTypeCustomResource;
import io.javaoperatorsdk.operator.sample.multiplereconcilersametype.MultipleReconcilerSameTypeReconciler1;
import io.javaoperatorsdk.operator.sample.multiplereconcilersametype.MultipleReconcilerSameTypeReconciler2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MultipleReconcilerSameTypeIT {

  public static final String TEST_RESOURCE_1 = "test1";
  public static final String TEST_RESOURCE_2 = "test2";
  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultipleReconcilerSameTypeReconciler1.class)
          .withReconciler(MultipleReconcilerSameTypeReconciler2.class)
          .build();


  @Test
  void multipleReconcilersBasedOnLeaderElection() {
    extension.create(testResource(TEST_RESOURCE_1, true));
    extension.create(testResource(TEST_RESOURCE_2, false));


    await().untilAsserted(() -> {
      assertThat(extension.getReconcilerOfType(MultipleReconcilerSameTypeReconciler1.class)
          .getNumberOfExecutions()).isEqualTo(1);
      assertThat(extension.getReconcilerOfType(MultipleReconcilerSameTypeReconciler2.class)
          .getNumberOfExecutions()).isEqualTo(1);

      var res1 = extension.get(MultipleReconcilerSameTypeCustomResource.class, TEST_RESOURCE_1);
      var res2 = extension.get(MultipleReconcilerSameTypeCustomResource.class, TEST_RESOURCE_2);
      assertThat(res1).isNotNull();
      assertThat(res2).isNotNull();
      assertThat(res1.getStatus().getReconciledBy())
          .isEqualTo(MultipleReconcilerSameTypeReconciler1.class.getSimpleName());
      assertThat(res2.getStatus().getReconciledBy())
          .isEqualTo(MultipleReconcilerSameTypeReconciler2.class.getSimpleName());
    });
  }

  MultipleReconcilerSameTypeCustomResource testResource(String name, boolean type1) {
    var res = new MultipleReconcilerSameTypeCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    if (type1) {
      res.getMetadata().getLabels().put("reconciler", "1");
    }
    return res;
  }

}
