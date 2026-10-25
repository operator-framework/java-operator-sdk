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
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Adds the finalizer with Server-Side Apply against a real API server, which is what decides
 * whether the apply creates a missing resource or rejects it.
 */
@EnableKubeAPIServer
class AddFinalizerWithSSAIntegrationTest {

  private static final String NAMESPACE = "default";
  private static final String FINALIZER = "int.test/finalizer";

  static KubernetesClient client;

  @Test
  void addsFinalizerToExistingResource() {
    var cached = createConfigMap("existing");

    var result = resourceOperationsFor(cached).addFinalizerWithSSA(FINALIZER);

    var actual = get("existing");
    assertThat(result.getMetadata().getFinalizers()).containsExactly(FINALIZER);
    assertThat(actual.getMetadata().getFinalizers()).containsExactly(FINALIZER);
    assertThat(actual.getData()).containsEntry("key", "value");
    removeFinalizerAndDelete(actual);
  }

  @Test
  void doesNotRecreateDeletedResource() {
    var cached = createConfigMap("deleted");
    client.resource(cached).delete();

    var result = resourceOperationsFor(cached).addFinalizerWithSSA(FINALIZER);

    assertThat(result).isNull();
    assertThat(get("deleted")).isNull();
  }

  @Test
  void addsFinalizerToResourceRecreatedWithSameName() {
    var cached = createConfigMap("recreated");
    client.resource(cached).delete();
    var recreated = createConfigMap("recreated");

    var result = resourceOperationsFor(cached).addFinalizerWithSSA(FINALIZER);

    var actual = get("recreated");
    assertThat(result.getMetadata().getUid()).isEqualTo(recreated.getMetadata().getUid());
    assertThat(actual.getMetadata().getUid()).isEqualTo(recreated.getMetadata().getUid());
    assertThat(actual.getMetadata().getFinalizers()).containsExactly(FINALIZER);
    removeFinalizerAndDelete(actual);
  }

  @Test
  void addsFinalizerUsingStaleCachedResource() {
    var cached = createConfigMap("stale");
    var updated = get("stale");
    updated.setData(Map.of("key", "updated"));
    client.resource(updated).update();

    resourceOperationsFor(cached).addFinalizerWithSSA(FINALIZER);

    var actual = get("stale");
    assertThat(actual.getMetadata().getFinalizers()).containsExactly(FINALIZER);
    assertThat(actual.getData()).containsEntry("key", "updated");
    removeFinalizerAndDelete(actual);
  }

  /**
   * Only the framework plumbing around the API call is mocked: the controller event source just
   * runs the write it is given, so the apply and the re-fetch on retry hit the real API server.
   */
  @SuppressWarnings("unchecked")
  private static ResourceOperations<ConfigMap> resourceOperationsFor(ConfigMap primary) {
    Context<ConfigMap> context = mock(Context.class);
    ControllerConfiguration<ConfigMap> controllerConfiguration =
        mock(ControllerConfiguration.class);
    var eventSourceRetriever = mock(EventSourceRetriever.class);
    ControllerEventSource<ConfigMap> controllerEventSource = mock(ControllerEventSource.class);

    when(context.getClient()).thenReturn(client);
    when(context.getPrimaryResource()).thenReturn(primary);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    when(controllerConfiguration.fieldManager()).thenReturn("finalizer-test");
    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(eventSourceRetriever.getControllerEventSource()).thenReturn(controllerEventSource);
    when(controllerEventSource.updateAndCacheResource(any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              UnaryOperator<ConfigMap> write = invocation.getArgument(1);
              return write.apply(invocation.getArgument(0));
            });
    return new ResourceOperations<>(context);
  }

  private static ConfigMap createConfigMap(String name) {
    return client
        .resource(
            new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(NAMESPACE)
                .endMetadata()
                .withData(Map.of("key", "value"))
                .build())
        .create();
  }

  private static ConfigMap get(String name) {
    return client.configMaps().inNamespace(NAMESPACE).withName(name).get();
  }

  private static void removeFinalizerAndDelete(ConfigMap configMap) {
    configMap.getMetadata().getFinalizers().clear();
    client.resource(configMap).update();
    client.resource(configMap).delete();
  }
}
