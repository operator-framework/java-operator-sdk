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
package io.javaoperatorsdk.operator.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

import static io.javaoperatorsdk.operator.api.config.LeaderElectionConfigurationBuilder.aLeaderElectionConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultEventRecorderTest {

  private static final String CONTROLLER = "testcontroller";
  private static final String INSTANCE = "operator-pod-1";

  private final List<Event> emitted = new ArrayList<>();
  private final DefaultEventRecorder recorder =
      new DefaultEventRecorder((event, context) -> emitted.add(event));

  @Test
  void fillsInEverythingDerivableFromTheControllerAndTheInvolvedObject() {
    recorder.record(
        configMap(), EventRecord.warning("Failed", "could not do the thing"), controller());

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
  void takesTheReportingControllerFromTheControllerConfiguration() {
    var controller = controller();
    when(controller.getName()).thenReturn("othercontroller");

    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller);

    assertThat(emitted.get(0).getReportingComponent()).isEqualTo("othercontroller");
    assertThat(emitted.get(0).getSource().getComponent()).isEqualTo("othercontroller");
  }

  @Test
  void fallsBackToTheHostNameWhenLeaderElectionConfiguresNoIdentity() {
    var controller = controller();
    when(controller.getConfigurationService().getLeaderElectionConfiguration())
        .thenReturn(Optional.empty());

    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller);

    assertThat(emitted.get(0).getReportingInstance())
        .isEqualTo(DefaultEventRecorder.defaultReportingInstance());
  }

  @Test
  void createsTheEventInTheNamespaceOfTheInvolvedObject() {
    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller());

    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("ns1");
    assertThat(emitted.get(0).getMetadata().getName()).startsWith("test1.");
  }

  @Test
  void recordsEventsForClusterScopedObjectsInTheDefaultNamespace() {
    recorder.record(clusterScoped(), EventRecord.normal("Created", "created"), controller());

    assertThat(emitted.get(0).getMetadata().getNamespace())
        .isEqualTo(DefaultEventRecorder.CLUSTER_SCOPED_EVENT_NAMESPACE);
    assertThat(emitted.get(0).getInvolvedObject().getNamespace()).isNull();
  }

  @Test
  void clusterScopedEventNamespaceCanBeOverridden() {
    recorder.record(
        clusterScoped(), EventRecord.normal("Created", "created"), controller("operator-ns"));

    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("operator-ns");
  }

  @Test
  void anOverriddenClusterScopedNamespaceDoesNotAffectNamespacedResources() {
    recorder.record(
        configMap(), EventRecord.normal("Created", "created"), controller("operator-ns"));

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
            .build(),
        controller());

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
            .build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getLabels()).containsEntry("group", "autoscaler");
    assertThat(emitted.get(0).getMetadata().getAnnotations()).containsEntry("recommendation", "4");
  }

  @Test
  void aFailingSinkNeverFailsTheCaller() {
    var failing =
        new DefaultEventRecorder(
            (event, context) -> {
              throw new RuntimeException("API server said no");
            });

    assertThatCode(
            () ->
                failing.record(configMap(), EventRecord.normal("Created", "created"), controller()))
        .doesNotThrowAnyException();
  }

  @Test
  void aFailingSinkFailsTheCallerWhenTheRecorderIsBuiltToFailOnError() {
    var failure = new RuntimeException("API server said no");
    var failing =
        DefaultEventRecorder.builder(
                (event, context) -> {
                  throw failure;
                })
            .failOnError(true)
            .build();

    assertThatThrownBy(
            () ->
                failing.record(configMap(), EventRecord.normal("Created", "created"), controller()))
        .isSameAs(failure);
  }

  @Test
  void aFailingSinkNeverFailsTheCallerWhenTheRecorderIsBuiltWithDefaults() {
    var failing =
        DefaultEventRecorder.builder(
                (event, context) -> {
                  throw new RuntimeException("API server said no");
                })
            .build();

    assertThatCode(
            () ->
                failing.record(configMap(), EventRecord.normal("Created", "created"), controller()))
        .doesNotThrowAnyException();
  }

  @Test
  void passesTheControllerConfigurationOnToTheSink() {
    var controllers = new ArrayList<ControllerConfiguration<?>>();
    var recording = new DefaultEventRecorder((event, controller) -> controllers.add(controller));
    var controller = controller();

    recording.record(configMap(), EventRecord.normal("Created", "created"), controller);

    assertThat(controllers).containsExactly(controller);
  }

  @Test
  void boundRecorderRecordsAboutTheObjectItIsBoundTo() {
    var bound = recorder.forResource(configMap(), controller());

    bound.normal("Created", "created");
    bound.warn("Failed", "failed");

    assertThat(emitted).hasSize(2);
    assertThat(emitted)
        .allSatisfy(e -> assertThat(e.getInvolvedObject().getName()).isEqualTo("test1"));
    assertThat(emitted.get(0).getType()).isEqualTo("Normal");
    assertThat(emitted.get(1).getType()).isEqualTo("Warning");
  }

  @Test
  void recordsEventsAboutAnObjectOtherThanThePrimaryResource() {
    var other =
        new ConfigMapBuilder()
            .withNewMetadata()
            .withName("parent")
            .withNamespace("ns2")
            .withUid("uid-parent")
            .endMetadata()
            .build();

    recorder.record(other, EventRecord.warning("ChildFailed", "child failed"), controller());

    assertThat(emitted).hasSize(1);
    var event = emitted.get(0);
    assertThat(event.getInvolvedObject().getKind()).isEqualTo("ConfigMap");
    assertThat(event.getInvolvedObject().getName()).isEqualTo("parent");
    assertThat(event.getInvolvedObject().getNamespace()).isEqualTo("ns2");
    assertThat(event.getInvolvedObject().getUid()).isEqualTo("uid-parent");
    assertThat(event.getMetadata().getNamespace()).isEqualTo("ns2");
    assertThat(event.getMetadata().getName()).startsWith("parent.");
    assertThat(event.getReportingComponent()).isEqualTo(CONTROLLER);
    assertThat(event.getReportingInstance()).isEqualTo(INSTANCE);
  }

  @Test
  void recordsEventsAboutClusterScopedObjectsOtherThanThePrimaryInTheConfiguredNamespace() {
    recorder.record(
        clusterScoped(), EventRecord.normal("Noticed", "noticed"), controller("kube-system"));

    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("kube-system");
    assertThat(emitted.get(0).getInvolvedObject().getKind()).isEqualTo("Namespace");
  }

  @Test
  void theSameRecordAboutDifferentObjectsSharingANameResolvesToDifferentEvents() {
    var record = EventRecord.normal("Noticed", "noticed");
    var controller = controller();
    var secret =
        new SecretBuilder()
            .withNewMetadata()
            .withName("test1")
            .withNamespace("ns1")
            .withUid("uid-secret")
            .endMetadata()
            .build();

    recorder.record(configMap(), record, controller);
    recorder.record(secret, record, controller);

    assertThat(emitted).hasSize(2);
    assertThat(emitted).allSatisfy(e -> assertThat(e.getMetadata().getName()).startsWith("test1."));
    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void rejectsAnObjectWithoutMetadata() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                recorder.record(
                    new ConfigMap(), EventRecord.normal("Noticed", "noticed"), controller()))
        .withMessageContaining("metadata");
    assertThat(emitted).isEmpty();
  }

  @Test
  void theControllerBoundRecorderRecordsAboutTheObjectItIsGiven() {
    var bound = recorder.forController(controller());

    bound.record(configMap(), EventRecord.normal("Created", "created"));
    bound.forResource(clusterScoped()).warn("Failed", "failed");

    assertThat(emitted).hasSize(2);
    assertThat(emitted.get(0).getInvolvedObject().getName()).isEqualTo("test1");
    assertThat(emitted.get(1).getInvolvedObject().getName()).isEqualTo("ns1");
    assertThat(emitted.get(1).getInvolvedObject().getKind()).isEqualTo("Namespace");
    assertThat(emitted)
        .allSatisfy(e -> assertThat(e.getReportingComponent()).isEqualTo(CONTROLLER));
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

    recorder.record(configMap, EventRecord.normal("Created", "created"), controller());

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
  void alwaysDerivesTheSameDefaultNameForTheSameEvent() {
    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller());

    assertThat(emitted.get(0).getMetadata().getName())
        .isEqualTo("test1.3c699548f37ff9cd6d2a786f64a27228");
  }

  @Test
  void namesEventsWithADnsSafeHashSuffix() {
    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller());

    assertThat(emitted.get(0).getMetadata().getName()).matches("test1\\.[0-9a-f]{32}");
  }

  @Test
  void givesEventsWhoseMessagesCollideUnderStringHashCodeDistinctNames() {
    // "Aa" and "BB" share a String.hashCode(), and so do the two identities they are part of: the
    // message comes last and both are of the same length, so the collision survives the common
    // prefix. Were the name suffix derived from that hash, the two events would resolve to one
    // name and the sink would take the second for a repeat of the first and drop it.
    assertThat("Aa".hashCode()).isEqualTo("BB".hashCode());

    recorder.record(configMap(), EventRecord.warning("Failed", "Aa"), controller());
    recorder.record(configMap(), EventRecord.warning("Failed", "BB"), controller());

    assertThat(emitted).hasSize(2);
    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void takesTheMessageOutOfTheEventIdentityWithADefaultKeyStrategy() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .keyStrategy(EventKeyStrategy.byReason())
            .build();
    var controller = controller();

    recorder.record(configMap(), EventRecord.warning("Failed", "first message"), controller);
    recorder.record(configMap(), EventRecord.warning("Failed", "second message"), controller);

    assertThat(emitted.get(0).getMetadata().getName())
        .isEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void prefersThePerRecordKeyOverTheDefaultKeyStrategy() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .keyStrategy(EventKeyStrategy.byReason())
            .build();
    var controller = controller();

    recorder.record(configMap(), EventRecord.warning("Failed", "message"), controller);
    recorder.record(
        configMap(),
        EventRecord.builder()
            .type(EventType.WARNING)
            .reason("Failed")
            .message("message")
            .key("another aggregate")
            .build(),
        controller);

    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void keepsTheMessageInTheEventIdentityWithoutADefaultKeyStrategy() {
    var controller = controller();

    recorder.record(configMap(), EventRecord.warning("Failed", "first message"), controller);
    recorder.record(configMap(), EventRecord.warning("Failed", "second message"), controller);

    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void keepsEventsWithTheSameReasonButDifferentTypesApartUnderByReason() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .keyStrategy(EventKeyStrategy.byReason())
            .build();
    var controller = controller();

    recorder.record(configMap(), EventRecord.normal("Flipped", "message"), controller);
    recorder.record(configMap(), EventRecord.warning("Flipped", "message"), controller);

    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void setsTheOwnerReferenceToTheInvolvedObjectWhenOwningEventsByRegarding() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .ownerReference(true)
            .build();

    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller());

    assertThat(emitted.get(0).getMetadata().getOwnerReferences())
        .singleElement()
        .satisfies(
            owner -> {
              assertThat(owner.getApiVersion()).isEqualTo("v1");
              assertThat(owner.getKind()).isEqualTo("ConfigMap");
              assertThat(owner.getName()).isEqualTo("test1");
              assertThat(owner.getUid()).isEqualTo("uid-1");
            });
  }

  @Test
  void carriesNoOwnerReferenceByDefault() {
    recorder.record(configMap(), EventRecord.normal("Created", "created"), controller());

    assertThat(emitted.get(0).getMetadata().getOwnerReferences()).isEmpty();
  }

  @Test
  void letsARecordOptOutOfTheRecorderLevelOwnerReference() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .ownerReference(true)
            .build();

    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").ownedByRegarding(false).build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getOwnerReferences()).isEmpty();
  }

  @Test
  void letsARecordOptIntoTheOwnerReferenceOnItsOwn() {
    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").ownedByRegarding(true).build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getOwnerReferences()).hasSize(1);
  }

  @Test
  void setsNoOwnerReferenceWhenTheRegardingObjectHasNoUidYet() {
    var withoutUid = configMap();
    withoutUid.getMetadata().setUid(null);

    recorder.record(
        withoutUid,
        EventRecord.builder().reason("Created").message("created").ownedByRegarding(true).build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getOwnerReferences()).isEmpty();
  }

  @Test
  void namesTheEventAfterThePerRecordNameWhenOneIsSet() {
    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name("my-event-name").build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).isEqualTo("my-event-name");
  }

  @Test
  void namesEventsThroughTheNamingStrategy() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .namingStrategy(
                (regarding, record) ->
                    Optional.of(regarding.getMetadata().getName() + "-" + record.reason()))
            .build();

    recorder.record(configMap(), EventRecord.warning("failed", "first"), controller());
    recorder.record(configMap(), EventRecord.warning("failed", "second"), controller());

    assertThat(emitted)
        .allSatisfy(event -> assertThat(event.getMetadata().getName()).isEqualTo("test1-failed"));
  }

  @Test
  void fallsBackToTheIdentityHashNameWhenTheNamingStrategyResolvesNothing() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .namingStrategy((regarding, record) -> Optional.empty())
            .build();

    recorder.record(configMap(), EventRecord.warning("Failed", "message"), controller());

    assertThat(emitted.get(0).getMetadata().getName())
        .startsWith("test1.")
        .hasSize("test1.".length() + 32);
  }

  @Test
  void truncatesSuppliedNamesToTheKubernetesNameLengthLimit() {
    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name("a".repeat(300)).build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).hasSize(253);
  }

  @Test
  void stripsTrailingSeparatorsFromTruncatedNames() {
    recorder.record(
        configMap(),
        EventRecord.builder()
            .reason("Created")
            .message("created")
            .name("a".repeat(219) + "." + "b".repeat(47))
            .build(),
        controller());
    recorder.record(
        configMap(),
        EventRecord.builder()
            .reason("Created")
            .message("created")
            .name("b".repeat(219) + "-" + "c".repeat(47))
            .build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).matches("a{219}\\.[0-9a-f]{32}");
    assertThat(emitted.get(1).getMetadata().getName()).matches("b{219}\\.[0-9a-f]{32}");
  }

  @Test
  void keepsLongSuppliedNamesSharingTheirPrefixDistinct() {
    recorder.record(
        configMap(),
        EventRecord.builder()
            .reason("Created")
            .message("created")
            .name("a".repeat(300) + "-one")
            .build(),
        controller());
    recorder.record(
        configMap(),
        EventRecord.builder()
            .reason("Created")
            .message("created")
            .name("a".repeat(300) + "-two")
            .build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).hasSize(253);
    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @Test
  void treatsABlankNameAsUnset() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .namingStrategy((regarding, record) -> Optional.of("strategy-name"))
            .build();

    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name("").build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).isEqualTo("strategy-name");
  }

  @Test
  void fallsBackToTheIdentityHashNameWhenEveryNameIsBlank() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .namingStrategy((regarding, record) -> Optional.of(" "))
            .build();

    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name("").build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).matches("test1\\.[0-9a-f]{32}");
  }

  @Test
  void fallsBackToTheIdentityHashNameWhenTruncationLeavesNothing() {
    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name(".".repeat(300)).build(),
        controller());

    assertThat(emitted.get(0).getMetadata().getName()).matches("test1\\.[0-9a-f]{32}");
  }

  @Test
  void fallsBackToTheIdentityHashNameWhenTheSuppliedNameIsInvalid() {
    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name("Status").build(),
        controller());
    recorder.record(
        configMap(),
        EventRecord.builder().reason("Created").message("created").name("status_name").build(),
        controller());

    assertThat(emitted)
        .allSatisfy(
            event -> assertThat(event.getMetadata().getName()).matches("test1\\.[0-9a-f]{32}"));
  }

  @Test
  void aFailingNamingStrategyNeverFailsTheCaller() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .namingStrategy(
                (regarding, record) -> {
                  throw new RuntimeException("cannot derive a name");
                })
            .build();

    assertThatCode(
            () ->
                recorder.record(
                    configMap(), EventRecord.normal("Created", "created"), controller()))
        .doesNotThrowAnyException();
    assertThat(emitted).isEmpty();
  }

  ControllerConfiguration<?> controller() {
    return controller(DefaultEventRecorder.CLUSTER_SCOPED_EVENT_NAMESPACE);
  }

  /**
   * The recorder reads everything it does not get from the {@link EventRecord} or the object the
   * event is about off the controller configuration: the name of the controller the event is
   * attributed to, and the configuration service the reporting instance and the cluster scoped
   * event namespace come from.
   */
  @SuppressWarnings("rawtypes")
  ControllerConfiguration<?> controller(String clusterScopedEventNamespace) {
    var configurationService = mock(ConfigurationService.class);
    when(configurationService.getLeaderElectionConfiguration())
        .thenReturn(
            Optional.of(aLeaderElectionConfiguration("lease").withIdentity(INSTANCE).build()));
    when(configurationService.clusterScopedEventNamespace())
        .thenReturn(clusterScopedEventNamespace);

    ControllerConfiguration controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getName()).thenReturn(CONTROLLER);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    return controllerConfiguration;
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
