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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.DefaultInformerPool;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.InformerClassifier;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The name an event source is known by in the pool decides two things: which pool entry it shares
 * (or, with a non-sharing pool, occupies), and the namespace its index names live in on a shared
 * informer. It therefore has to identify the event source, which {@link
 * InformerConfiguration#getName()} does not: that is {@code null} unless the event source was
 * explicitly named, which would collapse every unnamed event source of a controller onto one
 * identity.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedInformerEventSourcePoolIdentityTest {

  private static final String NAMESPACE = "ns1";
  private static final String CONTROLLER = "controller";

  private final KubernetesClient clientMock = MockKubernetesClient.client(Deployment.class);
  private final RecordingInformerPool pool = new RecordingInformerPool();

  @BeforeEach
  void setup() {
    SharedIndexInformer informer =
        clientMock
            .resources(Deployment.class)
            .inNamespace(NAMESPACE)
            .withLabelSelector((String) null)
            .withShardSelector(null)
            .runnableInformer(0);
    when(informer.getStore()).thenReturn(mock(Cache.class));
  }

  @Test
  void anUnnamedEventSourceIsIdentifiedByItsOwnGeneratedName() {
    var eventSource = startedEventSource();

    assertThat(eventSource.name()).isNotNull();
    assertThat(pool.acquiredNames)
        .as("the pool has to see the event source's own name, not the unset configured one")
        .containsExactly(eventSource.name());
  }

  @Test
  void twoUnnamedEventSourcesOfOneControllerAreIdentifiedDistinctly() {
    var first = startedEventSource();
    var second = startedEventSource();

    // identical configuration, so both resolve the same classifier and share one informer: only the
    // name keeps them apart, both as pool users and as owners of their index names
    assertThat(pool.acquiredNames).doesNotHaveDuplicates();
    assertThat(first.name()).isNotEqualTo(second.name());
  }

  @Test
  void releaseUsesTheSameNameAsTheAcquisition() {
    var eventSource = startedEventSource();

    eventSource.stop();

    // an asymmetry here would leave the pool holding a reference forever
    assertThat(pool.releasedNames).isEqualTo(pool.acquiredNames);
  }

  private InformerEventSource<Deployment, TestCustomResource> startedEventSource() {
    var configuration = mock(InformerEventSourceConfiguration.class);
    var informerConfig = mock(InformerConfiguration.class);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(Set.of(NAMESPACE));
    when(informerConfig.getInformerListLimit()).thenReturn(null);
    // the event source is not named, which is what makes the configured name null
    when(informerConfig.getName()).thenReturn(null);
    when(configuration.getInformerConfig()).thenReturn(informerConfig);
    when(configuration.getResourceClass()).thenReturn(Deployment.class);
    var secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(new ResourceID("name", NAMESPACE)));
    when(configuration.getSecondaryToPrimaryMapper()).thenReturn(secondaryToPrimaryMapper);

    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withKubernetesClient(clientMock).withInformerPool(pool));
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(controllerConfiguration.getName()).thenReturn(CONTROLLER);

    var eventSource = new InformerEventSource<Deployment, TestCustomResource>(configuration);
    eventSource.setEventHandler(mock(EventHandler.class));
    eventSource.setControllerConfiguration(controllerConfiguration);
    eventSource.start();
    return eventSource;
  }

  /** Records the identities the pool is asked about, and does not start the mocked informers. */
  private static class RecordingInformerPool extends DefaultInformerPool {

    private final List<String> acquiredNames = new CopyOnWriteArrayList<>();
    private final List<String> releasedNames = new CopyOnWriteArrayList<>();

    @Override
    public <R extends HasMetadata> SharedIndexInformer<R> getInformer(
        String controllerName, String name, InformerClassifier<R> classifier) {
      acquiredNames.add(name);
      return super.getInformer(controllerName, name, classifier);
    }

    @Override
    public <R extends HasMetadata> Optional<SharedIndexInformer<R>> releaseInformer(
        String controllerName, String name, InformerClassifier<R> classifier) {
      releasedNames.add(name);
      return super.releaseInformer(controllerName, name, classifier);
    }

    @Override
    public <R extends HasMetadata> void start(
        SharedIndexInformer<R> informer, InformerClassifier<R> classifier) {
      // the informers here are mocks, starting them would add nothing
    }
  }
}
