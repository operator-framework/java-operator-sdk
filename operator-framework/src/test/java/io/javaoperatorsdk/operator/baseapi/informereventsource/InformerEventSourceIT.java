package io.javaoperatorsdk.operator.baseapi.informereventsource;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.informereventsource.InformerEventSourceTestCustomReconciler.MISSING_CONFIG_MAP;
import static io.javaoperatorsdk.operator.baseapi.informereventsource.InformerEventSourceTestCustomReconciler.RELATED_RESOURCE_NAME;
import static io.javaoperatorsdk.operator.baseapi.informereventsource.InformerEventSourceTestCustomReconciler.TARGET_CONFIG_MAP_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

class InformerEventSourceIT {

  public static final String RESOURCE_NAME = "informertestcr";
  public static final String INITIAL_STATUS_MESSAGE = "Initial Status";
  public static final String UPDATE_STATUS_MESSAGE = "Updated Status";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new InformerEventSourceTestCustomReconciler())
          .build();

  @Test
  void testUsingInformerToWatchChangesOfConfigMap() {
    var customResource = initialCustomResource();
    customResource = operator.create(customResource);
    ConfigMap configMap = operator.create(relatedConfigMap(customResource.getMetadata().getName()));
    waitForCRStatusValue(INITIAL_STATUS_MESSAGE);

    configMap.getData().put(TARGET_CONFIG_MAP_KEY, UPDATE_STATUS_MESSAGE);
    operator.replace(configMap);

    waitForCRStatusValue(UPDATE_STATUS_MESSAGE);
  }

  @Test
  void deletingSecondaryResource() {
    var customResource = initialCustomResource();
    customResource = operator.create(customResource);
    waitForCRStatusValue(MISSING_CONFIG_MAP);
    ConfigMap configMap = operator.create(relatedConfigMap(customResource.getMetadata().getName()));
    waitForCRStatusValue(INITIAL_STATUS_MESSAGE);

    boolean res = operator.delete(configMap);
    if (!res) {
      fail("Unable to delete configmap");
    }

    waitForCRStatusValue(MISSING_CONFIG_MAP);
    assertThat(
            ((InformerEventSourceTestCustomReconciler) operator.getReconcilers().get(0))
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
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var cr = operator.get(InformerEventSourceTestCustomResource.class, RESOURCE_NAME);
              assertThat(cr.getStatus()).isNotNull();
              assertThat(cr.getStatus().getConfigMapValue()).isEqualTo(value);
            });
  }
}
