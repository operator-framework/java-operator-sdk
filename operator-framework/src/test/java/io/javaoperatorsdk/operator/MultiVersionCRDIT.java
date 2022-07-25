package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.multiversioncrd.MultiVersionCRDTestCustomResource1;
import io.javaoperatorsdk.operator.sample.multiversioncrd.MultiVersionCRDTestCustomResource2;
import io.javaoperatorsdk.operator.sample.multiversioncrd.MultiVersionCRDTestCustomResourceSpec1;
import io.javaoperatorsdk.operator.sample.multiversioncrd.MultiVersionCRDTestCustomResourceSpec2;
import io.javaoperatorsdk.operator.sample.multiversioncrd.MultiVersionCRDTestReconciler1;
import io.javaoperatorsdk.operator.sample.multiversioncrd.MultiVersionCRDTestReconciler2;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

class MultiVersionCRDIT {

  public static final String CR_V1_NAME = "crv1";
  public static final String CR_V2_NAME = "crv2";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultiVersionCRDTestReconciler1.class)
          .withReconciler(MultiVersionCRDTestReconciler2.class)
          .build();

  @Test
  void multipleCRDVersions() {
    operator.create(createTestResourceV1WithoutLabel());
    operator.create(createTestResourceV2WithLabel());

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(50))
        .until(
            () -> {
              var crV1Now = operator.get(MultiVersionCRDTestCustomResource1.class, CR_V1_NAME);
              var crV2Now = operator.get(MultiVersionCRDTestCustomResource2.class, CR_V2_NAME);
              return crV1Now.getStatus().getReconciledBy().size() == 1
                  && crV1Now.getStatus().getReconciledBy()
                      .contains(MultiVersionCRDTestReconciler1.class.getSimpleName())
                  && crV2Now.getStatus().getReconciledBy().size() == 1
                  && crV2Now.getStatus().getReconciledBy()
                      .contains(MultiVersionCRDTestReconciler2.class.getSimpleName());
            });
  }

  @Test
  void invalidEventsDoesNotBreakEventHandling() {
    var v2res = createTestResourceV2WithLabel();
    v2res.getMetadata().getLabels().clear();
    operator.create(v2res);
    var v1res = createTestResourceV1WithoutLabel();
    operator.create(v1res);

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(50))
        .until(() -> {
          var crV1Now = operator.get(MultiVersionCRDTestCustomResource1.class, CR_V1_NAME);
          return crV1Now.getStatus().getReconciledBy()
              .contains(MultiVersionCRDTestReconciler1.class.getSimpleName());
        });
    assertThat(
        operator
            .get(MultiVersionCRDTestCustomResource2.class, CR_V2_NAME)
            .getStatus())
        .isNull();
  }


  MultiVersionCRDTestCustomResource1 createTestResourceV1WithoutLabel() {
    MultiVersionCRDTestCustomResource1 cr = new MultiVersionCRDTestCustomResource1();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(CR_V1_NAME);
    cr.setSpec(new MultiVersionCRDTestCustomResourceSpec1());
    cr.getSpec().setValue(1);
    return cr;
  }

  MultiVersionCRDTestCustomResource2 createTestResourceV2WithLabel() {
    MultiVersionCRDTestCustomResource2 cr = new MultiVersionCRDTestCustomResource2();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(CR_V2_NAME);
    cr.getMetadata().setLabels(new HashMap<>());
    cr.getMetadata().getLabels().put("version", "v2");
    cr.setSpec(new MultiVersionCRDTestCustomResourceSpec2());
    cr.getSpec().setValue("string value");
    return cr;
  }
}
