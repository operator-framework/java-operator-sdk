package io.javaoperatorsdk.operator.workflow.crdpresentactivation;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Workflow Activation Based on CRD Presence",
    description =
        """
        Tests workflow activation conditions that depend on the presence of specific Custom \
        Resource Definitions (CRDs). Dependent resources are only created when their corresponding \
        CRDs exist in the cluster, allowing operators to gracefully handle optional dependencies \
        and multi-cluster scenarios.
        """)
public class CRDPresentActivationConditionIT {

  public static final String TEST_1 = "test1";
  public static final String CRD_NAME =
      "crdpresentactivationdependentcustomresources.sample.javaoperatorsdk";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CRDPresentActivationReconciler())
          .build();

  @Test
  void resourceCreatedOnlyIfCRDPresent() {
    // deleted so test can be repeated
    extension
        .getKubernetesClient()
        .resources(CustomResourceDefinition.class)
        .withName(CRD_NAME)
        .delete();

    var resource = extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var crd =
                  extension
                      .getKubernetesClient()
                      .resources(CustomResourceDefinition.class)
                      .withName(CRD_NAME)
                      .get();
              assertThat(crd).isNull();

              var dr = extension.get(CRDPresentActivationDependentCustomResource.class, TEST_1);
              assertThat(dr).isNull();
            });

    LocallyRunOperatorExtension.applyCrd(
        CRDPresentActivationDependentCustomResource.class, extension.getKubernetesClient());

    resource.getMetadata().setAnnotations(Map.of("sample", "value"));
    extension.replace(resource);

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var cm = extension.get(CRDPresentActivationDependentCustomResource.class, TEST_1);
              assertThat(cm).isNull();
            });
  }

  CRDPresentActivationCustomResource testResource() {
    var res = new CRDPresentActivationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    return res;
  }
}
