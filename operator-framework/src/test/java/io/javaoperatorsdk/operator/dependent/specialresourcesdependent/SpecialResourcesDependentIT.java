package io.javaoperatorsdk.operator.dependent.specialresourcesdependent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.specialresourcesdependent.SpecialResourceSpec.CHANGED_VALUE;
import static io.javaoperatorsdk.operator.dependent.specialresourcesdependent.SpecialResourceSpec.INITIAL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/*
 * Test for resources that are somehow special, currently mostly to cover the approach to handle
 * resources without spec. Not all the resources added here.
 */
@Sample(
    tldr = "Handling special Kubernetes resources without spec",
    description =
        """
        Demonstrates how to handle special built-in Kubernetes resources like ServiceAccount that \
        don't have a spec field. These resources require different handling approaches \
        since their configuration is stored directly in the resource body rather than in a \
        spec section.
        """)
public class SpecialResourcesDependentIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SpecialResourceTestReconciler())
          .build();

  @Test
  void specialCRUDReconciler() {
    var resource = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var sa = extension.get(ServiceAccount.class, RESOURCE_NAME);
              assertThat(sa).isNotNull();
              assertThat(sa.getAutomountServiceAccountToken()).isTrue();
            });

    resource.getSpec().setValue(CHANGED_VALUE);
    extension.replace(resource);

    await()
        .untilAsserted(
            () -> {
              var sa = extension.get(ServiceAccount.class, RESOURCE_NAME);
              assertThat(sa).isNotNull();
              assertThat(sa.getAutomountServiceAccountToken()).isFalse();
            });
  }

  SpecialResourceCustomResource testResource() {
    SpecialResourceCustomResource res = new SpecialResourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new SpecialResourceSpec());
    res.getSpec().setValue(INITIAL_VALUE);
    return res;
  }
}
