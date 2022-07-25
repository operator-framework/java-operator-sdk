package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.customfilter.CustomFilteringTestReconciler;
import io.javaoperatorsdk.operator.sample.customfilter.CustomFilteringTestResource;
import io.javaoperatorsdk.operator.sample.customfilter.CustomFilteringTestResourceSpec;

import static org.assertj.core.api.Assertions.assertThat;

class CustomResourceFilterIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new CustomFilteringTestReconciler())
          .build();

  @Test
  void doesCustomFiltering() throws InterruptedException {
    var filtered1 = createTestResource("filtered1", true, false);
    var filtered2 = createTestResource("filtered2", false, true);
    var notFiltered = createTestResource("notfiltered", true, true);
    operator.create(filtered1);
    operator.create(filtered2);
    operator.create(notFiltered);

    Thread.sleep(300);

    assertThat(
        ((CustomFilteringTestReconciler) operator.getReconcilers().get(0)).getNumberOfExecutions())
        .isEqualTo(1);
  }


  CustomFilteringTestResource createTestResource(String name, boolean filter1, boolean filter2) {
    CustomFilteringTestResource resource = new CustomFilteringTestResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName(name);
    resource.setSpec(new CustomFilteringTestResourceSpec());
    resource.getSpec().setFilter1(filter1);
    resource.getSpec().setFilter2(filter2);
    return resource;
  }

}
