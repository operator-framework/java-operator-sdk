package io.javaoperatorsdk.operator.workflow.manageddependentdeletecondition;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Managed Dependent Delete Condition",
    description =
        """
        Demonstrates how to use delete conditions to control when dependent resources can be \
        deleted. This test shows how the primary resource deletion can be blocked until \
        dependent resources are properly cleaned up, ensuring graceful shutdown and \
        preventing orphaned resources.
        """)
public class ManagedDependentDeleteConditionIT {

  public static final String RESOURCE_NAME = "test1";
  public static final String CUSTOM_FINALIZER = "test/customfinalizer";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withDefaultNonSSAResource(Set.of()))
          .withReconciler(new ManagedDependentDefaultDeleteConditionReconciler())
          .build();

  @Test
  void resourceNotDeletedUntilDependentDeleted() {
    var resource = new ManagedDependentDefaultDeleteConditionCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource = extension.create(resource);

    await()
        .timeout(Duration.ofSeconds(300))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              var sec = extension.get(Secret.class, RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(sec).isNotNull();
            });

    var secret = extension.get(Secret.class, RESOURCE_NAME);
    secret.getMetadata().getFinalizers().add(CUSTOM_FINALIZER);
    secret = extension.replace(secret);

    extension.delete(resource);

    // both resources are present until the finalizer removed
    await()
        .pollDelay(Duration.ofMillis(250))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              var sec = extension.get(Secret.class, RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(sec).isNotNull();
            });

    secret.getMetadata().getFinalizers().clear();
    extension.replace(secret);

    await()
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              var sec = extension.get(Secret.class, RESOURCE_NAME);
              assertThat(cm).isNull();
              assertThat(sec).isNull();
            });
  }
}
