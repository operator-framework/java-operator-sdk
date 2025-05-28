package io.javaoperatorsdk.operator.baseapi.labelselector;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.labelselector.LabelSelectorTestReconciler.LABEL_KEY;
import static io.javaoperatorsdk.operator.baseapi.labelselector.LabelSelectorTestReconciler.LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LabelSelectorIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new LabelSelectorTestReconciler())
          .build();

  @Test
  void filtersCustomResourceByLabel() {
    operator.create(resource("r1", true));
    operator.create(resource("r2", false));

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              assertThat(
                      operator
                          .getReconcilerOfType(LabelSelectorTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
            });
  }

  LabelSelectorTestCustomResource resource(String name, boolean addLabel) {
    var res = new LabelSelectorTestCustomResource();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(name)
            .withLabels(addLabel ? Map.of(LABEL_KEY, LABEL_VALUE) : Collections.emptyMap())
            .build());
    return res;
  }
}
