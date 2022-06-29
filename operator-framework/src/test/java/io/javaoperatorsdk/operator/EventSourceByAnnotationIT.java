package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.eventsourcebyannotation.observedgeneration.EventSourceByAnnotationCustomResource;
import io.javaoperatorsdk.operator.sample.eventsourcebyannotation.observedgeneration.EventSourceByAnnotationReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class EventSourceByAnnotationIT {

  public static final String NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(EventSourceByAnnotationReconciler.class)
          .build();

  @Test
  void registerEventSourceWithAnnotation() {
    operator.create(EventSourceByAnnotationCustomResource.class, customResource());
    await().pollDelay(Duration.ofMillis(150)).untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(EventSourceByAnnotationReconciler.class)
          .getNumberOfExecution())
          .isEqualTo(2); // first reconcile + config map creation
    });

    var cm = operator.get(ConfigMap.class, NAME);
    cm.setData(Map.of("key", "newvalue"));
    operator.replace(ConfigMap.class, cm);

    await().pollDelay(Duration.ofMillis(150)).untilAsserted(() -> {
      assertThat(operator.getReconcilerOfType(EventSourceByAnnotationReconciler.class)
          .getNumberOfExecution())
          .isEqualTo(3);
    });
  }

  EventSourceByAnnotationCustomResource customResource() {
    EventSourceByAnnotationCustomResource resource = new EventSourceByAnnotationCustomResource();
    resource.setMetadata(new ObjectMetaBuilder()
        .withName(NAME)
        .build());
    return resource;
  }



}
