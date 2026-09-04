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
package io.javaoperatorsdk.operator.processing.event.source.informer.pool;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.informer.FieldSelector;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceOtherV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the identity semantics of {@link InformerClassifier}. The classifier is used as
 * the key that decides whether two controllers share a single pooled informer, so its equality
 * contract (in particular that {@code informerListLimit} is intentionally excluded and that the
 * client is compared by identity) is load-bearing.
 */
class InformerClassifierTest {

  private static final KubernetesClient CLIENT = mock(KubernetesClient.class);
  private static final String LABEL = "app=foo";
  private static final String SHARD = "shard-1";
  private static final String NAMESPACE = "default";
  private static final GroupVersionKind GVK = new GroupVersionKind("sample.io/v1", "Foo");
  private static final FieldSelector FIELD_SELECTOR =
      new FieldSelector(new FieldSelector.Field("status.phase", "Running"));
  private static final Long LIMIT = 100L;
  private static final ItemStore<TestCustomResource> ITEM_STORE = mock(ItemStore.class);

  private static InformerClassifier<TestCustomResource> base() {
    return new InformerClassifier<>(
        CLIENT,
        LABEL,
        SHARD,
        NAMESPACE,
        TestCustomResource.class,
        GVK,
        FIELD_SELECTOR,
        LIMIT,
        ITEM_STORE);
  }

  @Test
  void classifiersWithIdenticalFieldsAreEqual() {
    assertThat(base()).isEqualTo(base());
    assertThat(base()).hasSameHashCodeAs(base());
  }

  @Test
  void informerListLimitIsExcludedFromEqualityAndHashCode() {
    var withOtherLimit =
        new InformerClassifier<>(
            CLIENT,
            LABEL,
            SHARD,
            NAMESPACE,
            TestCustomResource.class,
            GVK,
            FIELD_SELECTOR,
            999L,
            ITEM_STORE);

    assertThat(base()).isEqualTo(withOtherLimit);
    assertThat(base()).hasSameHashCodeAs(withOtherLimit);
  }

  @Test
  void toStringContainsTheApiServerUrlDerivedFromTheClient() {
    // the URL is not a component of its own, but classifiers are logged and the client alone does
    // not tell which cluster it connects to
    var config = mock(Config.class);
    when(config.getMasterUrl()).thenReturn("https://localhost:8443/");
    var client = mock(KubernetesClient.class);
    when(client.getConfiguration()).thenReturn(config);

    var classifier =
        new InformerClassifier<>(
            client,
            LABEL,
            SHARD,
            NAMESPACE,
            TestCustomResource.class,
            GVK,
            FIELD_SELECTOR,
            LIMIT,
            ITEM_STORE);

    assertThat(classifier.toString())
        .contains("https://localhost:8443/")
        .contains(NAMESPACE)
        .contains(LABEL)
        .contains(TestCustomResource.class.getName());
  }

  @Test
  void toStringDoesNotFailWithoutAClient() {
    var classifier =
        new InformerClassifier<>(
            null,
            LABEL,
            SHARD,
            NAMESPACE,
            TestCustomResource.class,
            GVK,
            FIELD_SELECTOR,
            LIMIT,
            ITEM_STORE);

    assertThat(classifier.toString()).contains(NAMESPACE);
  }

  @Test
  void differsWhenClientDiffers() {
    // two distinct clients may differ in credentials, impersonation or TLS material even when they
    // report the same master URL, so they must never end up sharing an informer
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                mock(KubernetesClient.class),
                LABEL,
                SHARD,
                NAMESPACE,
                TestCustomResource.class,
                GVK,
                FIELD_SELECTOR,
                LIMIT,
                ITEM_STORE));
  }

  @Test
  void differsWhenLabelSelectorDiffers() {
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                CLIENT,
                "app=bar",
                SHARD,
                NAMESPACE,
                TestCustomResource.class,
                GVK,
                FIELD_SELECTOR,
                LIMIT,
                ITEM_STORE));
  }

  @Test
  void differsWhenShardSelectorDiffers() {
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                CLIENT,
                LABEL,
                "shard-2",
                NAMESPACE,
                TestCustomResource.class,
                GVK,
                FIELD_SELECTOR,
                LIMIT,
                ITEM_STORE));
  }

  @Test
  void differsWhenNamespaceDiffers() {
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                CLIENT,
                LABEL,
                SHARD,
                "other-ns",
                TestCustomResource.class,
                GVK,
                FIELD_SELECTOR,
                LIMIT,
                ITEM_STORE));
  }

  @Test
  void differsWhenResourceClassDiffers() {
    // item stores are null here because their generic type is tied to the resource class, which is
    // exactly the field under test; this keeps the resource class the only difference.
    var forTestResource =
        new InformerClassifier<>(
            CLIENT,
            LABEL,
            SHARD,
            NAMESPACE,
            TestCustomResource.class,
            GVK,
            FIELD_SELECTOR,
            LIMIT,
            null);
    var forOtherResource =
        new InformerClassifier<>(
            CLIENT,
            LABEL,
            SHARD,
            NAMESPACE,
            TestCustomResourceOtherV1.class,
            GVK,
            FIELD_SELECTOR,
            LIMIT,
            null);

    assertThat(forTestResource).isNotEqualTo(forOtherResource);
  }

  @Test
  void differsWhenGroupVersionKindDiffers() {
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                CLIENT,
                LABEL,
                SHARD,
                NAMESPACE,
                TestCustomResource.class,
                new GroupVersionKind("sample.io/v1", "Bar"),
                FIELD_SELECTOR,
                LIMIT,
                ITEM_STORE));
  }

  @Test
  void differsWhenFieldSelectorDiffers() {
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                CLIENT,
                LABEL,
                SHARD,
                NAMESPACE,
                TestCustomResource.class,
                GVK,
                new FieldSelector(new FieldSelector.Field("status.phase", "Pending")),
                LIMIT,
                ITEM_STORE));
  }

  @Test
  void differsWhenItemStoreDiffers() {
    assertThat(base())
        .isNotEqualTo(
            new InformerClassifier<>(
                CLIENT,
                LABEL,
                SHARD,
                NAMESPACE,
                TestCustomResource.class,
                GVK,
                FIELD_SELECTOR,
                LIMIT,
                mock(ItemStore.class)));
  }

  @Test
  void differsOnlyByInformerListLimitIsTrueWhenOnlyLimitDiffers() {
    var withOtherLimit =
        new InformerClassifier<>(
            CLIENT,
            LABEL,
            SHARD,
            NAMESPACE,
            TestCustomResource.class,
            GVK,
            FIELD_SELECTOR,
            999L,
            ITEM_STORE);

    assertThat(base().differsOnlyByInformerListLimit(withOtherLimit)).isTrue();
  }

  @Test
  void differsOnlyByInformerListLimitIsFalseWhenFullyEqual() {
    assertThat(base().differsOnlyByInformerListLimit(base())).isFalse();
  }

  @Test
  void differsOnlyByInformerListLimitIsFalseWhenAnotherFieldDiffers() {
    // different namespace AND different limit: not "only by limit"
    var differentNamespaceAndLimit =
        new InformerClassifier<>(
            CLIENT,
            LABEL,
            SHARD,
            "other-ns",
            TestCustomResource.class,
            GVK,
            FIELD_SELECTOR,
            999L,
            ITEM_STORE);

    assertThat(base().differsOnlyByInformerListLimit(differentNamespaceAndLimit)).isFalse();
  }
}
