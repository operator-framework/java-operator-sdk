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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the informer creation that {@link AbstractInformerPool} performs for every pool,
 * as opposed to the sharing strategy a concrete pool adds on top (covered by {@link
 * DefaultInformerPoolTest} and {@link NonSharingInformerPoolTest}). {@link NonSharingInformerPool}
 * is used merely as the simplest concrete subclass to reach that inherited behavior through.
 */
class AbstractInformerPoolTest {

  private static final String CONTROLLER = "controller";
  private static final String ES_NAME = "event-source";
  private static final String NAMESPACE = "default";

  private final KubernetesClient client = MockKubernetesClient.client(TestCustomResource.class);
  private final NonSharingInformerPool pool = new NonSharingInformerPool();

  @BeforeEach
  void setUp() {
    pool.setConfigurationService(new BaseConfigurationService());
  }

  @Test
  void keepsTheNamespaceIndexWhenTheClassifierDoesNotAskForIt() {
    var informer = pool.getInformer(CONTROLLER, ES_NAME, classifier(false));

    verify(informer, never()).removeNamespaceIndex();
  }

  @Test
  void removesTheNamespaceIndexWhenTheClassifierAsksForIt() {
    var informer = pool.getInformer(CONTROLLER, ES_NAME, classifier(true));

    verify(informer).removeNamespaceIndex();
  }

  private InformerClassifier<TestCustomResource> classifier(boolean withoutNamespaceIndex) {
    return new InformerClassifier<>(
        client,
        null,
        null,
        NAMESPACE,
        TestCustomResource.class,
        null,
        null,
        null,
        null,
        withoutNamespaceIndex);
  }
}
