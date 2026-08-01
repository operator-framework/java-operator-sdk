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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The informers of an event source are acquired from the pool before any of them is started, so a
 * failure while starting them has to release everything that was already acquired. Otherwise the
 * pooled informer stays referenced forever: {@code start()} never marks the event source running,
 * so {@code stop()} silently skips the release.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedInformerEventSourceStartFailureTest {

  private static final Set<String> NAMESPACES = Set.of("ns1", "ns2");

  private final KubernetesClient clientMock = MockKubernetesClient.client(Deployment.class);
  private final FailingOnceInformerPool pool = new FailingOnceInformerPool();
  private final InformerEventSourceConfiguration<Deployment> configuration =
      mock(InformerEventSourceConfiguration.class);

  @BeforeEach
  void setup() {
    final var informerConfig = mock(InformerConfiguration.class);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(NAMESPACES);
    // an unconfigured informer has no list limit, while a plain Long-returning mock would yield 0
    // here and send the pool down the withLimit(...) branch
    when(informerConfig.getInformerListLimit()).thenReturn(null);
    when(configuration.getInformerConfig()).thenReturn(informerConfig);
    when(configuration.getResourceClass()).thenReturn(Deployment.class);
    final var secondaryToPrimaryMapper = mock(SecondaryToPrimaryMapper.class);
    when(secondaryToPrimaryMapper.toPrimaryResourceIDs(any()))
        .thenReturn(Set.of(new ResourceID("name", "ns1")));
    when(configuration.getSecondaryToPrimaryMapper()).thenReturn(secondaryToPrimaryMapper);
    stubInformerStore();
  }

  /**
   * A successfully started {@link InformerEventSource} lists its cache to populate the
   * primary-to-secondary index, and the mock client leaves the informer's store unstubbed.
   */
  private void stubInformerStore() {
    SharedIndexInformer informer =
        clientMock
            .resources(Deployment.class)
            .inNamespace("ns1")
            .withLabelSelector((String) null)
            .withShardSelector(null)
            .runnableInformer(0);
    when(informer.getStore()).thenReturn(mock(Cache.class));
  }

  @Test
  void releasesAlreadyAcquiredInformersWhenStartupFails() {
    var eventSource = buildEventSource();

    assertThatThrownBy(eventSource::start).isInstanceOf(RuntimeException.class);

    assertThat(eventSource.isRunning()).isFalse();
    assertThat(pool.numberOfInformersForResource(Deployment.class))
        .as("a failed start must not leave informers referenced in the pool")
        .isZero();
  }

  @Test
  void doesNotAcquireTwiceWhenStartIsRetriedAfterAFailure() {
    var eventSource = buildEventSource();

    assertThatThrownBy(eventSource::start).isInstanceOf(RuntimeException.class);
    // a failed event source is started again, e.g. by a subsequent dynamic registration
    eventSource.start();
    assertThat(eventSource.isRunning()).isTrue();
    assertThat(pool.numberOfInformersForResource(Deployment.class)).isEqualTo(NAMESPACES.size());

    eventSource.stop();

    assertThat(pool.numberOfInformersForResource(Deployment.class))
        .as("the retried start must not have acquired a second reference per informer")
        .isZero();
  }

  private InformerEventSource<Deployment, TestCustomResource> buildEventSource() {
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withKubernetesClient(clientMock).withInformerPool(pool));
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(controllerConfiguration.getName()).thenReturn("controller");

    var eventSource = new InformerEventSource<Deployment, TestCustomResource>(configuration);
    eventSource.setEventHandler(mock(EventHandler.class));
    eventSource.setControllerConfiguration(controllerConfiguration);
    return eventSource;
  }

  /** Fails the very first informer startup, so later attempts succeed. */
  private static class FailingOnceInformerPool extends DefaultInformerPool {

    private final AtomicBoolean failNextStart = new AtomicBoolean(true);

    @Override
    public <R extends HasMetadata> void start(
        SharedIndexInformer<R> informer, InformerClassifier<R> classifier) {
      if (failNextStart.compareAndSet(true, false)) {
        throw new OperatorException("simulated informer startup failure");
      }
      // not delegating on purpose: the pool's start is the seam under test, actually starting the
      // mocked informer would add nothing
    }
  }
}
