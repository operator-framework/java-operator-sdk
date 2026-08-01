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
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the reference-counting / sharing behavior of {@link DefaultInformerPool}: a single
 * informer is created and shared per classifier, and it is stopped only when the last user releases
 * it.
 */
class DefaultInformerPoolTest {

  private static final String CONTROLLER = "controller";
  private static final String ES_NAME = "event-source";

  private final KubernetesClient client = MockKubernetesClient.client(TestCustomResource.class);
  private final DefaultInformerPool pool = new DefaultInformerPool();

  @BeforeEach
  void setup() {
    pool.setConfigurationService(new BaseConfigurationService());
  }

  @Test
  void sharesSingleInformerForTheSameClassifier() {
    var classifier = classifier("default");

    var first = pool.getInformer(CONTROLLER, ES_NAME, classifier);
    var second = pool.getInformer("other-controller", "other-es", classifier);

    assertThat(first).isSameAs(second);
    assertThat(pool.size()).isEqualTo(1);
    assertThat(pool.numberOfInformersForResource(TestCustomResource.class)).isEqualTo(1);
    // the underlying informer must be created exactly once, not once per user
    verify(client, times(1)).resources(TestCustomResource.class);
  }

  @Test
  void createsSeparateInformersForDifferentClassifiers() {
    pool.getInformer(CONTROLLER, ES_NAME, classifier("ns1"));
    pool.getInformer(CONTROLLER, ES_NAME, classifier("ns2"));

    assertThat(pool.size()).isEqualTo(2);
    assertThat(pool.numberOfInformersForResource(TestCustomResource.class)).isEqualTo(2);
    verify(client, times(2)).resources(TestCustomResource.class);
  }

  @Test
  void createsSeparateInformersForDifferentClientsWithTheSameApiServerUrl() {
    var otherClient = MockKubernetesClient.client(TestCustomResource.class);
    // the two clients are indistinguishable by URL, they are two different instances though and may
    // well differ in credentials or TLS material, so they must not end up sharing an informer
    assertThat(otherClient.getConfiguration().getMasterUrl()).isEqualTo(masterUrl());

    pool.getInformer(CONTROLLER, ES_NAME, classifier("default"));
    pool.getInformer("other-controller", "other-es", classifier(otherClient, "default"));

    assertThat(pool.size()).isEqualTo(2);
    verify(client, times(1)).resources(TestCustomResource.class);
    verify(otherClient, times(1)).resources(TestCustomResource.class);
  }

  @Test
  void sharesInformerWhenClassifiersDifferOnlyByListLimit() {
    var withLimit100 =
        new InformerClassifier<>(
            client, null, null, "default", TestCustomResource.class, null, null, 100L, null);
    var withLimit200 =
        new InformerClassifier<>(
            client, null, null, "default", TestCustomResource.class, null, null, 200L, null);

    var first = pool.getInformer(CONTROLLER, ES_NAME, withLimit100);
    var second = pool.getInformer("other-controller", "other-es", withLimit200);

    assertThat(first).isSameAs(second);
    assertThat(pool.size()).isEqualTo(1);
    verify(client, times(1)).resources(TestCustomResource.class);
  }

  @Test
  void doesNotStopSharedInformerUntilLastRelease() {
    var classifier = classifier("default");
    var informer = pool.getInformer(CONTROLLER, ES_NAME, classifier);
    pool.getInformer("other-controller", "other-es", classifier);

    pool.releaseInformer(CONTROLLER, ES_NAME, classifier);
    verify(informer, never()).stop();
    assertThat(pool.size()).isEqualTo(1);

    pool.releaseInformer("other-controller", "other-es", classifier);
    verify(informer, times(1)).stop();
    assertThat(pool.size()).isZero();
  }

  @Test
  void releaseReturnsInformerEvenWhileStillShared() {
    var classifier = classifier("default");
    var informer = pool.getInformer(CONTROLLER, ES_NAME, classifier);
    pool.getInformer("other-controller", "other-es", classifier);

    // the caller needs the (still-running) informer back so it can remove its own event handler
    var released = pool.releaseInformer(CONTROLLER, ES_NAME, classifier);

    assertThat(released).containsSame(informer);
    verify(informer, never()).stop();
  }

  @Test
  void releaseOfUnknownClassifierReturnsEmptyAndDoesNotThrow() {
    var released = pool.releaseInformer(CONTROLLER, ES_NAME, classifier("never-registered"));

    assertThat(released).isEmpty();
    assertThat(pool.size()).isZero();
  }

  private String masterUrl() {
    return client.getConfiguration().getMasterUrl();
  }

  private InformerClassifier<TestCustomResource> classifier(String namespace) {
    return classifier(client, namespace);
  }

  private InformerClassifier<TestCustomResource> classifier(
      KubernetesClient forClient, String namespace) {
    return new InformerClassifier<>(
        forClient, null, null, namespace, TestCustomResource.class, null, null, null, null);
  }
}
