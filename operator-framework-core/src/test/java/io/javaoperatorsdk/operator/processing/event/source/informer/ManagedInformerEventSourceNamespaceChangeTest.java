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

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
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
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A namespace change acquires and starts pooled informers, so it must not happen on an event source
 * that is no longer running: {@link ManagedInformerEventSource#stop()} short-circuits on a
 * non-running event source, which would leave those informers referenced with nothing left to
 * release them.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedInformerEventSourceNamespaceChangeTest {

  private final KubernetesClient clientMock = MockKubernetesClient.client(Deployment.class);
  private final DefaultInformerPool pool = new DefaultInformerPool();
  private final InformerEventSourceConfiguration<Deployment> configuration =
      mock(InformerEventSourceConfiguration.class);

  @BeforeEach
  void setup() {
    final var informerConfig = mock(InformerConfiguration.class);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(Set.of("ns1"));
    when(informerConfig.getInformerListLimit()).thenReturn(null);
    when(configuration.getInformerConfig()).thenReturn(informerConfig);
    when(configuration.getResourceClass()).thenReturn(Deployment.class);
    when(configuration.followControllerNamespaceChanges()).thenReturn(true);
    final var secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(new ResourceID("name", "ns1")));
    when(configuration.getSecondaryToPrimaryMapper()).thenReturn(secondaryToPrimaryMapper);
    stubInformerStore();
  }

  @Test
  void namespaceChangeOnAStoppedEventSourceAcquiresNoInformer() {
    var eventSource = buildEventSource();
    eventSource.start();
    assertThat(pool.numberOfInformersForResource(Deployment.class)).isEqualTo(1);
    eventSource.stop();
    assertThat(pool.numberOfInformersForResource(Deployment.class)).isZero();

    eventSource.changeNamespaces(Set.of("ns2"));

    assertThat(pool.numberOfInformersForResource(Deployment.class))
        .as("a stopped event source must not acquire informers it can never release")
        .isZero();
  }

  @Test
  void namespaceChangeIsStillAppliedWhileRunning() {
    var eventSource = buildEventSource();
    eventSource.start();

    eventSource.changeNamespaces(Set.of("ns2"));

    assertThat(pool.numberOfInformersForResource(Deployment.class)).isEqualTo(1);
    assertThat(eventSource.manager().isWatchingNamespace("ns2")).isTrue();
    assertThat(eventSource.manager().isWatchingNamespace("ns1")).isFalse();

    eventSource.stop();
    assertThat(pool.numberOfInformersForResource(Deployment.class)).isZero();
  }

  /**
   * Fabric8 replays the cache of an already populated informer to a newly added handler
   * synchronously, on the thread registering it. That replay enters the event source's monitor,
   * which the namespace change holds while waiting for the informers to start, so the handler must
   * be registered on the thread holding the monitor rather than on an informer starter thread.
   */
  @Test
  void namespaceChangeJoiningAPopulatedInformerDoesNotDeadlock() {
    var eventHandler = mock(EventHandler.class);
    var eventSource = buildEventSource(eventHandler);
    eventSource.start();
    replayCacheOnHandlerRegistration();

    assertTimeoutPreemptively(
        Duration.ofSeconds(10), () -> eventSource.changeNamespaces(Set.of("ns1", "ns2")));

    assertThat(eventSource.manager().isWatchingNamespace("ns2")).isTrue();
    verify(eventHandler).handleEvent(any());
    eventSource.stop();
  }

  private void replayCacheOnHandlerRegistration() {
    var informer = informer();
    var existing = new Deployment();
    existing.setMetadata(
        new ObjectMetaBuilder()
            .withName("existing")
            .withNamespace("ns2")
            .withResourceVersion("1")
            .build());
    doAnswer(
            invocation -> {
              ResourceEventHandler<Deployment> handler = invocation.getArgument(0);
              handler.onAdd(existing);
              return null;
            })
        .when(informer)
        .addEventHandler(any());
  }

  /**
   * A successfully started {@link InformerEventSource} lists its cache to populate the
   * primary-to-secondary index, and the mock client leaves the informer's store unstubbed.
   */
  private void stubInformerStore() {
    var informer = informer();
    when(informer.getStore()).thenReturn(mock(Cache.class));
  }

  /** The mock client hands out the very same informer for every namespace. */
  private SharedIndexInformer informer() {
    return clientMock
            .resources(Deployment.class)
            .inNamespace("ns1")
            .withLabelSelector((String) null)
            .withShardSelector(null)
            .runnableInformer(0);
  }

  private InformerEventSource<Deployment, TestCustomResource> buildEventSource() {
    return buildEventSource(mock(EventHandler.class));
  }

  private InformerEventSource<Deployment, TestCustomResource> buildEventSource(
      EventHandler eventHandler) {
    ConfigurationService configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withKubernetesClient(clientMock).withInformerPool(pool));
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(controllerConfiguration.getName()).thenReturn("controller");

    var eventSource = new InformerEventSource<Deployment, TestCustomResource>(configuration);
    eventSource.setEventHandler(eventHandler);
    eventSource.setControllerConfiguration(controllerConfiguration);
    return eventSource;
  }
}
