/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.api.events;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DefaultEventRecorderTest {

  private static final String CONTROLLER = "testcontroller";
  private static final String INSTANCE = "operator-pod-1";

  private final List<Event> emitted = new ArrayList<>();
  private final DefaultEventRecorder recorder =
      new DefaultEventRecorder(CONTROLLER, INSTANCE, emitted::add);

  @Test
  void fillsInEverythingDerivableFromTheControllerAndTheInvolvedObject() {
    recorder.record(configMap(), EventRecord.warning("Failed", "could not do the thing"));

    assertThat(emitted).hasSize(1);
    var event = emitted.get(0);
    assertThat(event.getType()).isEqualTo("Warning");
    assertThat(event.getReason()).isEqualTo("Failed");
    assertThat(event.getMessage()).isEqualTo("could not do the thing");
    assertThat(event.getCount()).isEqualTo(1);
    assertThat(event.getReportingComponent()).isEqualTo(CONTROLLER);
    assertThat(event.getReportingInstance()).isEqualTo(INSTANCE);
    assertThat(event.getSource().getComponent()).isEqualTo(CONTROLLER);
    assertThat(event.getFirstTimestamp()).isNotNull().isEqualTo(event.getLastTimestamp());

    var involved = event.getInvolvedObject();
    assertThat(involved.getKind()).isEqualTo("ConfigMap");
    assertThat(involved.getApiVersion()).isEqualTo("v1");
    assertThat(involved.getName()).isEqualTo("test1");
    assertThat(involved.getNamespace()).isEqualTo("ns1");
    assertThat(involved.getUid()).isEqualTo("uid-1");
    assertThat(involved.getResourceVersion()).isEqualTo("42");
  }

  @Test
  void createsTheEventInTheNamespaceOfTheInvolvedObject() {
    recorder.record(configMap(), EventRecord.normal("Created", "created"));

    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("ns1");
    assertThat(emitted.get(0).getMetadata().getName()).startsWith("test1.");
  }

  @Test
  void recordsEventsForClusterScopedObjectsInTheDefaultNamespace() {
    recorder.record(clusterScoped(), EventRecord.normal("Created", "created"));

    assertThat(emitted.get(0).getMetadata().getNamespace())
        .isEqualTo(DefaultEventRecorder.CLUSTER_SCOPED_EVENT_NAMESPACE);
    assertThat(emitted.get(0).getInvolvedObject().getNamespace()).isNull();
  }

  @Test
  void clusterScopedEventNamespaceCanBeOverridden() {
    var configured = new DefaultEventRecorder(CONTROLLER, INSTANCE, "operator-ns", emitted::add);

    configured.record(clusterScoped(), EventRecord.normal("Created", "created"));

    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("operator-ns");
  }

  @Test
  void anOverriddenClusterScopedNamespaceDoesNotAffectNamespacedResources() {
    var configured = new DefaultEventRecorder(CONTROLLER, INSTANCE, "operator-ns", emitted::add);

    configured.record(configMap(), EventRecord.normal("Created", "created"));

    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("ns1");
  }

  @Test
  void perEventReportingComponentOverridesTheControllerName() {
    recorder.record(
        configMap(),
        EventRecord.builder()
            .reason("Submitted")
            .message("submitted")
            .reportingComponent("JobManagerDeployment")
            .action("Submit")
            .build());

    assertThat(emitted.get(0).getReportingComponent()).isEqualTo("JobManagerDeployment");
    assertThat(emitted.get(0).getSource().getComponent()).isEqualTo("JobManagerDeployment");
    assertThat(emitted.get(0).getAction()).isEqualTo("Submit");
    // the reporting instance is never overridable per event
    assertThat(emitted.get(0).getReportingInstance()).isEqualTo(INSTANCE);
  }

  @Test
  void passesLabelsAndAnnotationsThrough() {
    recorder.record(
        configMap(),
        EventRecord.builder()
            .reason("Scaling")
            .message("scaling up")
            .label("group", "autoscaler")
            .annotation("recommendation", "4")
            .build());

    assertThat(emitted.get(0).getMetadata().getLabels()).containsEntry("group", "autoscaler");
    assertThat(emitted.get(0).getMetadata().getAnnotations()).containsEntry("recommendation", "4");
  }

  @Test
  void aFailingSinkNeverFailsTheCaller() {
    var failing =
        new DefaultEventRecorder(
            CONTROLLER,
            INSTANCE,
            event -> {
              throw new RuntimeException("API server said no");
            });

    assertThatCode(() -> failing.record(configMap(), EventRecord.normal("Created", "created")))
        .doesNotThrowAnyException();
  }

  @Test
  void boundRecorderRecordsAboutTheBoundObject() {
    var bound = recorder.forResource(configMap());

    bound.normal("Created", "created");
    bound.warn("Failed", "failed");

    assertThat(emitted).hasSize(2);
    assertThat(emitted)
        .allSatisfy(e -> assertThat(e.getInvolvedObject().getName()).isEqualTo("test1"));
    assertThat(emitted.get(0).getType()).isEqualTo("Normal");
    assertThat(emitted.get(1).getType()).isEqualTo("Warning");
  }

  @Test
  void truncatesTheNameOfTheInvolvedObjectToStayWithinTheKubernetesNameLimit() {
    var longName = "a".repeat(253);
    var configMap =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName(longName)
            .withNamespace("ns1")
            .endMetadata()
            .build();

    recorder.record(configMap, EventRecord.normal("Created", "created"));

    var name = emitted.get(0).getMetadata().getName();
    assertThat(name).hasSizeLessThanOrEqualTo(253);
    assertThat(name).startsWith("a");
    // the involved object itself keeps its full name, only the event name is shortened
    assertThat(emitted.get(0).getInvolvedObject().getName()).isEqualTo(longName);
  }

  @Test
  void reasonIsRequired() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> EventRecord.builder().message("no reason given").build());
  }

  @Test
  void namesEventsWithADnsSafeHashSuffix() {
    recorder.record(configMap(), EventRecord.normal("Created", "created"));

    assertThat(emitted.get(0).getMetadata().getName()).matches("test1\\.[0-9a-f]{32}");
  }

  @Test
  void givesEventsWhoseMessagesCollideUnderStringHashCodeDistinctNames() {
    // "Aa" and "BB" share a String.hashCode(), and so do the two identities they are part of: the
    // message comes last and both are of the same length, so the collision survives the common
    // prefix. Were the name suffix derived from that hash, the two events would resolve to one
    // name and the sink would take the second for a repeat of the first and drop it.
    assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());

    recorder.record(configMap(), EventRecord.warning("Failed", "Aa"));
    recorder.record(configMap(), EventRecord.warning("Failed", "BB"));

    assertThat(emitted).hasSize(2);
    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  ConfigMap configMap() {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName("test1")
        .withNamespace("ns1")
        .withUid("uid-1")
        .withResourceVersion("42")
        .endMetadata()
        .build();
  }

  Namespace clusterScoped() {
    return new NamespaceBuilder()
        .withNewMetadata()
        .withName("ns1")
        .withUid("uid-2")
        .endMetadata()
        .build();
  }
}
