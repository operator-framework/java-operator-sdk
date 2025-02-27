package io.javaoperatorsdk.operator.dependent.ssalegacymatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class SSAWithLegacyMatcherIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SSALegacyMatcherReconciler())
          .build();

  @Test
  void matchesDependentWithLegacyMatcher() {
    var resource = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var service = extension.get(Service.class, TEST_RESOURCE_NAME);
              assertThat(service).isNotNull();
              assertThat(ServiceDependentResource.createUpdateCount.get()).isEqualTo(1);
            });

    resource.getSpec().setValue("other_value");

    await()
        .untilAsserted(
            () -> {
              assertThat(ServiceDependentResource.createUpdateCount.get()).isEqualTo(1);
            });
  }

  SSALegacyMatcherCustomResource testResource() {
    SSALegacyMatcherCustomResource res = new SSALegacyMatcherCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new SSALegacyMatcherSpec());
    res.getSpec().setValue("initial-value");
    return res;
  }
}
