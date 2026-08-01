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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link NonSharingInformerPool}: unlike {@link DefaultInformerPool} it never shares
 * informers. A distinct informer is created for every {@code controllerName}+{@code name}+{@code
 * classifier} combination, and release is scoped to that same combination.
 */
class NonSharingInformerPoolTest {

  private static final String CONTROLLER = "controller";
  private static final String ES_NAME = "event-source";

  private final KubernetesClient client = MockKubernetesClient.client(TestCustomResource.class);
  private final NonSharingInformerPool pool = new NonSharingInformerPool();

  @BeforeEach
  void setUp() {
    pool.setConfigurationService(new BaseConfigurationService());
  }

  @Test
  void createsSeparateInformerForSameClassifierFromDifferentControllers() {
    var classifier = classifier("default");

    pool.getInformer(CONTROLLER, ES_NAME, classifier);
    pool.getInformer("other-controller", ES_NAME, classifier);

    // no sharing: one informer created per user even for an identical classifier
    assertThat(pool.size()).isEqualTo(2);
    verify(client, times(2)).resources(TestCustomResource.class);
  }

  @Test
  void createsSeparateInformerForDifferentEventSourceNames() {
    var classifier = classifier("default");

    pool.getInformer(CONTROLLER, "event-source-1", classifier);
    pool.getInformer(CONTROLLER, "event-source-2", classifier);

    assertThat(pool.size()).isEqualTo(2);
    verify(client, times(2)).resources(TestCustomResource.class);
  }

  @Test
  void throwsWhenRequestingAnInformerForAnAlreadyRegisteredKey() {
    var classifier = classifier("default");

    pool.getInformer(CONTROLLER, ES_NAME, classifier);

    // requesting the same controller+event source+classifier combination again without releasing
    // first would otherwise silently overwrite the map entry and leak the earlier informer
    assertThatThrownBy(() -> pool.getInformer(CONTROLLER, ES_NAME, classifier))
        .isInstanceOf(OperatorException.class)
        .hasMessageContaining(CONTROLLER)
        .hasMessageContaining(ES_NAME)
        .hasMessageContaining(classifier.toString());

    // the earlier informer is left untouched, still registered exactly once
    assertThat(pool.size()).isEqualTo(1);
    verify(client, times(1)).resources(TestCustomResource.class);
  }

  @Test
  void allowsReRequestingAnInformerAfterItWasReleased() {
    var classifier = classifier("default");
    pool.getInformer(CONTROLLER, ES_NAME, classifier);
    pool.releaseInformer(CONTROLLER, ES_NAME, classifier);

    // must not throw: releasing frees up the key for reuse
    pool.getInformer(CONTROLLER, ES_NAME, classifier);

    assertThat(pool.size()).isEqualTo(1);
    verify(client, times(2)).resources(TestCustomResource.class);
  }

  @Test
  void releaseStopsAndRemovesTheInformer() {
    var classifier = classifier("default");
    var informer = pool.getInformer(CONTROLLER, ES_NAME, classifier);

    var released = pool.releaseInformer(CONTROLLER, ES_NAME, classifier);

    assertThat(released).containsSame(informer);
    verify(informer, times(1)).stop();
    assertThat(pool.size()).isZero();
  }

  @Test
  void releaseIsScopedToControllerAndName() {
    var classifier = classifier("default");
    var informer = pool.getInformer(CONTROLLER, ES_NAME, classifier);

    // same classifier but a different controller: nothing is released or stopped
    var released = pool.releaseInformer("other-controller", ES_NAME, classifier);

    assertThat(released).isEmpty();
    verify(informer, never()).stop();
    assertThat(pool.size()).isEqualTo(1);
  }

  @Test
  void releaseOfUnknownInformerReturnsEmptyAndDoesNotThrow() {
    var released = pool.releaseInformer(CONTROLLER, ES_NAME, classifier("never-registered"));

    assertThat(released).isEmpty();
    assertThat(pool.size()).isZero();
  }

  private InformerClassifier<TestCustomResource> classifier(String namespace) {
    return new InformerClassifier<>(
        client, null, null, namespace, TestCustomResource.class, null, null, null, null);
  }
}
