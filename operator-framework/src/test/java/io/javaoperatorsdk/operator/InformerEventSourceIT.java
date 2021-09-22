package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomResource;
import io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomResourceController;

import static io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomResourceController.RELATED_RESOURCE_UID;
import static io.javaoperatorsdk.operator.sample.informereventsource.InformerEventSourceTestCustomResourceController.TARGET_CONFIG_MAP_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class InformerEventSourceIT {

  public static final String RESOURCE_NAME = "informertestcr";
  public static final String INITIAL_STATUS_MESSAGE = "Initial Status";
  public static final String UPDATE_STATUS_MESSAGE = "Updated Status";

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withController(new InformerEventSourceTestCustomResourceController())
          .build();

  @Test
  public void testUsingInformerToWatchChangesOfConfigMap() {
    var customResource = initialCustomResource();
    customResource = operator.create(InformerEventSourceTestCustomResource.class, customResource);
    ConfigMap configMap =
        operator.create(ConfigMap.class, relatedConfigMap(customResource.getMetadata().getUid()));
    waitForCRStatusValue(INITIAL_STATUS_MESSAGE);

    configMap.getData().put(TARGET_CONFIG_MAP_KEY, UPDATE_STATUS_MESSAGE);
    operator.replace(ConfigMap.class, configMap);

    waitForCRStatusValue(UPDATE_STATUS_MESSAGE);
  }

  private ConfigMap relatedConfigMap(String relatedResourceAnnotation) {
    ConfigMap configMap = new ConfigMap();

    ObjectMeta objectMeta = new ObjectMeta();
    objectMeta.setName(RESOURCE_NAME);
    objectMeta.setAnnotations(new HashMap<>());
    objectMeta.getAnnotations().put(RELATED_RESOURCE_UID, relatedResourceAnnotation);
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
          operator.getNamedResource(InformerEventSourceTestCustomResource.class, RESOURCE_NAME);
      assertThat(cr.getStatus()).isNotNull();
      assertThat(cr.getStatus().getConfigMapValue()).isEqualTo(value);
    });
  }

}
