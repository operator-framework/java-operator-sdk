package io.javaoperatorsdk.operator.dependent.desiredonce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DesiredOnceIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(DesiredOnceReconciler.class).build();

  @Test
  public void checkThatDesiredIsOnlyCalledOncePerReconciliation() {
    var resource = operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm = operator.get(ConfigMap.class, DesiredOnceDependent.getName(resource));
              assertThat(cm).isNotNull();
              assertThat(cm.getData().get(DesiredOnce.KEY)).isEqualTo(DesiredOnce.VALUE);
            });
  }

  private DesiredOnce testResource() {
    var res = new DesiredOnce(DesiredOnce.VALUE);
    res.setMetadata(
        new ObjectMetaBuilder().withName("test").withNamespace(operator.getNamespace()).build());
    return res;
  }
}
