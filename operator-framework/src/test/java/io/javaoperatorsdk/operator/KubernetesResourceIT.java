package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.configmap.ConfigMapReconciler;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class KubernetesResourceIT {

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withReconciler(new ConfigMapReconciler())
          .build();

  @Test
  public void testReconciliationOfNonCustomResource() {
    operator.create(ConfigMap.class, testConfigMap());
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      // there is an additional default config map in the namespace
      assertThat(
          ((TestExecutionInfoProvider) operator.getReconcilers().get(0)).getNumberOfExecutions())
              .isEqualTo(2);
    });
  }

  private ConfigMap testConfigMap() {
    ConfigMap resource = new ConfigMap();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-config-map")
            .build());

    Map<String, String> data = new HashMap<>();
    data.put("sampleKey", "sampleValue");
    resource.setData(data);
    return resource;
  }

}
