package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.optionaldependent.OptionalDependentCustomResource;
import io.javaoperatorsdk.operator.sample.optionaldependent.OptionalDependentReconciler;
import io.javaoperatorsdk.operator.sample.optionaldependent.OptionalDependentSecondaryCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class OptionalDependentIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new OptionalDependentReconciler())
          .build();

  @AfterEach
  void cleanup() {
    LocallyRunOperatorExtension.deleteCrd(OptionalDependentSecondaryCustomResource.class,
        extension.getKubernetesClient());
  }

  @Test
  void activatesResourceAfterCRDApplied() throws InterruptedException {
    var r = extension.create(testResource());

    await().pollDelay(Duration.ofMillis(200)).untilAsserted(() -> {
      var secondary =
          extension.get(OptionalDependentSecondaryCustomResource.class, r.getMetadata().getName());
      assertThat(secondary).isNull();
    });
    LocallyRunOperatorExtension.applyCrd(OptionalDependentSecondaryCustomResource.class,
        extension.getKubernetesClient());

    await().untilAsserted(() -> {
      assertThat(extension.getKubernetesClient().resources(CustomResourceDefinition.class)
          .withName("optionaldependentsecondarycustomresources.sample.javaoperatorsdk").get())
          .isNotNull();
    });

    // triggering reconciliation explicitly
    r.getMetadata().getAnnotations().put("trigger", "true");
    extension.replace(r);

    await().untilAsserted(() -> {
      var secondary =
          extension.get(OptionalDependentSecondaryCustomResource.class, r.getMetadata().getName());
      assertThat(secondary).isNotNull();
    });

    extension.delete(r);

    await().timeout(Duration.ofSeconds(180)).untilAsserted(() -> {
      var secondary =
          extension.get(OptionalDependentSecondaryCustomResource.class, r.getMetadata().getName());
      assertThat(secondary).isNull();
    });
  }

  private OptionalDependentCustomResource testResource() {
    var res = new OptionalDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName("test1")
        .build());
    return res;
  }

}
