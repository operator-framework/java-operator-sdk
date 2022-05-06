package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocalOperatorExtension;
import io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomResource;

import static io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomReconciler.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

class InformerEventSourceIT {

  public static final String RESOURCE_NAME = "informertestcr";
  public static final String INITIAL_STATUS_MESSAGE = "Initial Status";
  public static final String UPDATE_STATUS_MESSAGE = "Updated Status";

  @RegisterExtension
  LocalOperatorExtension operator =
      LocalOperatorExtension.builder()
          .withReconciler(new InformerEventSourceTestCustomReconciler())
          .build();

  @Test
  void testUsingInformerToWatchChangesOfConfigMap() {
    var customResource = initialCustomResource();
    customResource = operator.create(InformerEventSourceTestCustomResource.class, customResource);
    ConfigMap configMap =
        operator.create(ConfigMap.class, relatedConfigMap(customResource.getMetadata().getName()));
    waitForCRStatusValue(INITIAL_STATUS_MESSAGE);

    configMap.getData().put(TARGET_CONFIG_MAP_KEY, UPDATE_STATUS_MESSAGE);
    operator.replace(ConfigMap.class, configMap);

    waitForCRStatusValue(UPDATE_STATUS_MESSAGE);
  }

  @Test
  void deletingSecondaryResource() {
    var customResource = initialCustomResource();
    customResource = operator.create(InformerEventSourceTestCustomResource.class, customResource);
    waitForCRStatusValue(MISSING_CONFIG_MAP);
    ConfigMap configMap =
        operator.create(ConfigMap.class, relatedConfigMap(customResource.getMetadata().getName()));
    waitForCRStatusValue(INITIAL_STATUS_MESSAGE);

    boolean res = operator.delete(ConfigMap.class, configMap);
    if (!res) {
      fail("Unable to delete configmap");
    }

    waitForCRStatusValue(MISSING_CONFIG_MAP);
    assertThat(((InformerEventSourceTestCustomReconciler) operator.getReconcilers().get(0))
        .getNumberOfExecutions())
            .isEqualTo(3);
  }

  private ConfigMap relatedConfigMap(String relatedResourceAnnotation) {
    ConfigMap configMap = new ConfigMap();

    ObjectMeta objectMeta = new ObjectMeta();
    objectMeta.setName(RESOURCE_NAME);
    objectMeta.setAnnotations(new HashMap<>());
    objectMeta.getAnnotations().put(RELATED_RESOURCE_NAME, relatedResourceAnnotation);
    configMap.setMetadata(objectMeta);

    configMap.setData(new HashMap<>());
    configMap.getData().put(TARGET_CONFIG_MAP_KEY, INITIAL_STATUS_MESSAGE);
    return configMap;
  }

  private InformerEventSourceTestCustomResource initialCustomResource() {
    var customResource = new InformerEventSourceTestCustomResource();
    ObjectMeta objectMeta = new ObjectMeta();
    objectMeta.setName(RESOURCE_NAME);
    customResource.setMetadata(objectMeta);
    return customResource;
  }

  private void waitForCRStatusValue(String value) {
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      var cr =
          operator.get(InformerEventSourceTestCustomResource.class, RESOURCE_NAME);
      assertThat(cr.getStatus()).isNotNull();
      assertThat(cr.getStatus().getConfigMapValue()).isEqualTo(value);
    });
  }

}
