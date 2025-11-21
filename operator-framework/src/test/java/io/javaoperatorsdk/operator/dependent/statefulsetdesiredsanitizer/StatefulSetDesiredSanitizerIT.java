package io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Sanitizing StatefulSet desired state for SSA",
    description =
        """
        Shows how to properly sanitize StatefulSet resources before using Server-Side Apply. \
        StatefulSets have immutable fields and server-managed fields that need to be \
        removed from the desired state to prevent conflicts and unnecessary updates.
        """)
public class StatefulSetDesiredSanitizerIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new StatefulSetDesiredSanitizerReconciler())
          .build();

  @Test
  void testSSAMatcher() {
    var resource = extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var statefulSet = extension.get(StatefulSet.class, TEST_1);
              assertThat(statefulSet).isNotNull();
            });
    // make sure reconciliation happens at least once more
    resource.getSpec().setValue("changed value");
    extension.replace(resource);

    await()
        .untilAsserted(
            () ->
                assertThat(StatefulSetDesiredSanitizerDependentResource.nonMatchedAtLeastOnce)
                    .isFalse());
  }

  StatefulSetDesiredSanitizerCustomResource testResource() {
    var res = new StatefulSetDesiredSanitizerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new StatefulSetDesiredSanitizerSpec());
    res.getSpec().setValue("initial value");

    return res;
  }
}
