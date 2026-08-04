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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.FieldsV1;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the opt-in behavior enabled by {@link
 * KubernetesDependentResourceConfig#detectApiVersionChange()}: a dependent resource is considered
 * mismatched when the API version marker last applied by the operator differs from the one it would
 * currently apply, without causing updates on every reconciliation once the marker is up-to-date.
 */
class KubernetesDependentResourceApiVersionChangeTest {

  private static final String FIELD_MANAGER = "controller";
  private static final String OLD_API_VERSION = "example.com/v1alpha1";
  private static final String NEW_API_VERSION = "example.com/v1";

  @Test
  void featureDisabledByDefaultDoesNotAddMarker() {
    var dr = newDependentResource(false);
    var context = context(false);

    var actual = widget(NEW_API_VERSION, null, 3);
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched()).isTrue();
    assertThat(desired.getMetadata().getAnnotations())
        .doesNotContainKey(KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY);
  }

  @Test
  void featureDisabledIgnoresPreExistingMarkerMismatch() {
    var dr = newDependentResource(false);
    var context = context(false);

    var actual =
        widget(
            NEW_API_VERSION,
            Map.of(
                KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                OLD_API_VERSION),
            3);
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("Disabled feature must reproduce current, unaffected behavior")
        .isTrue();
  }

  @Test
  void nonSSA_missingMarkerCausesMismatchAndMarksDesired() {
    var dr = newDependentResource(true);
    var context = context(false);

    var actual = widget(NEW_API_VERSION, null, 3);
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("A resource with no marker annotation must be considered mismatched")
        .isFalse();
    assertThat(desired.getMetadata().getAnnotations())
        .containsEntry(
            KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, NEW_API_VERSION);
  }

  @Test
  void nonSSA_matchingMarkerMatches() {
    var dr = newDependentResource(true);
    var context = context(false);

    var actual =
        widget(
            NEW_API_VERSION,
            Map.of(
                KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                NEW_API_VERSION),
            3);
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched()).isTrue();
  }

  @Test
  void nonSSA_staleMarkerCausesMismatch() {
    var dr = newDependentResource(true);
    var context = context(false);

    var actual =
        widget(
            NEW_API_VERSION,
            Map.of(
                KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                OLD_API_VERSION),
            3);
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("A stale marker must cause an update to be requested")
        .isFalse();
    assertThat(desired.getMetadata().getAnnotations())
        .withFailMessage("The desired resource must be marked with the new API version")
        .containsEntry(
            KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, NEW_API_VERSION);
  }

  @Test
  void nonSSA_matchingMarkerStillDetectsUnrelatedSpecChanges() {
    var dr = newDependentResource(true);
    var context = context(false);

    var actual =
        widget(
            NEW_API_VERSION,
            Map.of(
                KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                NEW_API_VERSION),
            3);
    var desired = widget(NEW_API_VERSION, null, 4);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("Normal spec matching must remain intact regardless of the marker")
        .isFalse();
  }

  @Test
  void nonSSA_missingApiVersionOnDesiredIsHandledSafely() {
    var dr = newDependentResource(true);
    var context = context(false);

    var actual = widget(NEW_API_VERSION, null, 3);
    var desired = widget(null, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("A null desired API version must not prevent normal matching")
        .isTrue();
    assertThat(desired.getMetadata().getAnnotations())
        .doesNotContainKey(KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY);
  }

  @Test
  void nonSSA_preservesExistingAnnotationsWhenMarkingEvenIfImmutable() {
    var dr = newDependentResource(true);
    var context = context(false);

    var actual = widget(NEW_API_VERSION, null, 3);
    var desired = widget(NEW_API_VERSION, null, 3);
    // simulate a desired resource whose annotations map is immutable, as returned by Map.of(...)
    desired.getMetadata().setAnnotations(Map.of("user.example.com/owner", "team-a"));

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("A missing marker must still cause a mismatch")
        .isFalse();
    assertThat(desired.getMetadata().getAnnotations())
        .withFailMessage("Existing annotations must be preserved alongside the new marker")
        .containsEntry("user.example.com/owner", "team-a")
        .containsEntry(
            KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, NEW_API_VERSION);
  }

  @Test
  void ssa_missingMarkerCausesMismatchAndMarksDesired() {
    var dr = newDependentResource(true);
    var context = context(true);

    var actual = widget(NEW_API_VERSION, null, 3);
    actual.getMetadata().setManagedFields(List.of(managedFieldsEntry(false)));
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("A resource applied before the marker existed must be updated once")
        .isFalse();
    assertThat(desired.getMetadata().getAnnotations())
        .containsEntry(
            KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY, NEW_API_VERSION);
  }

  @Test
  void ssa_matchingMarkerMatches() {
    var dr = newDependentResource(true);
    var context = context(true);

    var actual =
        widget(
            NEW_API_VERSION,
            Map.of(
                KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                NEW_API_VERSION),
            3);
    actual.getMetadata().setManagedFields(List.of(managedFieldsEntry(true)));
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("No further update should be requested once the marker is up-to-date")
        .isTrue();
  }

  @Test
  void ssa_staleMarkerCausesMismatch() {
    var dr = newDependentResource(true);
    var context = context(true);

    var actual =
        widget(
            NEW_API_VERSION,
            Map.of(
                KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                OLD_API_VERSION),
            3);
    actual.getMetadata().setManagedFields(List.of(managedFieldsEntry(true)));
    var desired = widget(NEW_API_VERSION, null, 3);

    var result = dr.match(actual, desired, primary(), context);

    assertThat(result.matched())
        .withFailMessage("A stale marker recorded via SSA must still cause a mismatch")
        .isFalse();
  }

  private static WidgetDependentResourceForTest newDependentResource(
      boolean detectApiVersionChange) {
    var dr = new WidgetDependentResourceForTest();
    dr.configureWith(
        new KubernetesDependentResourceConfigBuilder<GenericKubernetesResource>()
            .withDetectApiVersionChange(detectApiVersionChange)
            .build());
    return dr;
  }

  private static HasMetadata primary() {
    return mock();
  }

  @SuppressWarnings("unchecked")
  private static Context<HasMetadata> context(boolean useSSA) {
    Context<HasMetadata> context = mock();
    var client = MockKubernetesClient.client(HasMetadata.class);
    when(context.getClient()).thenReturn(client);

    var configurationService = mock(ConfigurationService.class);
    when(configurationService.shouldUseSSA(any(), any(), any())).thenReturn(useSSA);
    ControllerConfiguration<HasMetadata> controllerConfiguration = mock();
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(controllerConfiguration.fieldManager()).thenReturn(FIELD_MANAGER);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    return context;
  }

  private static GenericKubernetesResource widget(
      String apiVersion, Map<String, String> annotations, int specSize) {
    var resource = new GenericKubernetesResource();
    resource.setApiVersion(apiVersion);
    resource.setKind("Widget");
    var metadataBuilder = new ObjectMetaBuilder().withName("test").withNamespace("default");
    if (annotations != null) {
      metadataBuilder.withAnnotations(annotations);
    }
    resource.setMetadata(metadataBuilder.build());
    resource.setAdditionalProperty("spec", Map.of("size", specSize));
    return resource;
  }

  private static ManagedFieldsEntry managedFieldsEntry(boolean managesAnnotation) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("f:spec", Map.of("f:size", Map.of()));
    if (managesAnnotation) {
      fields.put(
          "f:metadata",
          Map.of(
              "f:annotations",
              Map.of(
                  "f:" + KubernetesDependentResource.LAST_APPLIED_API_VERSION_ANNOTATION_KEY,
                  Map.of())));
    }
    var fieldsV1 = new FieldsV1();
    fieldsV1.setAdditionalProperties(fields);

    var entry = new ManagedFieldsEntry();
    entry.setManager(FIELD_MANAGER);
    entry.setOperation("Apply");
    entry.setFieldsV1(fieldsV1);
    return entry;
  }

  private static class WidgetDependentResourceForTest
      extends KubernetesDependentResource<GenericKubernetesResource, HasMetadata> {
    public WidgetDependentResourceForTest() {
      super(GenericKubernetesResource.class, null);
    }
  }
}
