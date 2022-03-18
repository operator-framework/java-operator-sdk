package io.javaoperatorsdk.operator.processing.event.source.informer;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static org.assertj.core.api.Assertions.assertThat;

class EventRecorderTest {

  public static final String RESOURCE_VERSION = "0";
  public static final String RESOURCE_VERSION1 = "1";
  EventRecorder<ConfigMap> eventRecorder = new EventRecorder();

  ConfigMap testConfigMap = testConfigMap(RESOURCE_VERSION);
  ConfigMap testConfigMap2 = testConfigMap(RESOURCE_VERSION1);

  ResourceID id = ResourceID.fromResource(testConfigMap);

  @Test
  void recordsEvents() {

    assertThat(eventRecorder.isRecordingFor(id)).isFalse();

    eventRecorder.startEventRecording(id);
    assertThat(eventRecorder.isRecordingFor(id)).isTrue();

    eventRecorder.recordEvent(testConfigMap);

    eventRecorder.stopEventRecording(id);
    assertThat(eventRecorder.isRecordingFor(id)).isFalse();
  }

  @Test
  void getsLastRecorded() {
    eventRecorder.startEventRecording(id);

    eventRecorder.recordEvent(testConfigMap);
    eventRecorder.recordEvent(testConfigMap2);

    assertThat(eventRecorder.getLastEvent(id)).isEqualTo(testConfigMap2);
  }

  @Test
  void checksContainsWithResourceVersion() {
    eventRecorder.startEventRecording(id);

    eventRecorder.recordEvent(testConfigMap);
    eventRecorder.recordEvent(testConfigMap2);

    assertThat(eventRecorder.containsEventWithResourceVersion(id, RESOURCE_VERSION)).isTrue();
    assertThat(eventRecorder.containsEventWithResourceVersion(id, RESOURCE_VERSION1)).isTrue();
    assertThat(eventRecorder.containsEventWithResourceVersion(id, "xxx")).isFalse();
  }

  @Test
  void checkLastItemVersion() {
    eventRecorder.startEventRecording(id);

    eventRecorder.recordEvent(testConfigMap);
    eventRecorder.recordEvent(testConfigMap2);

    assertThat(eventRecorder.containsEventWithVersionButItsNotLastOne(id, RESOURCE_VERSION))
        .isTrue();
    assertThat(eventRecorder.containsEventWithVersionButItsNotLastOne(id, RESOURCE_VERSION1))
        .isFalse();
  }

  ConfigMap testConfigMap(String resourceVersion) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName("test");
    configMap.getMetadata().setResourceVersion(resourceVersion);

    return configMap;
  }

}
