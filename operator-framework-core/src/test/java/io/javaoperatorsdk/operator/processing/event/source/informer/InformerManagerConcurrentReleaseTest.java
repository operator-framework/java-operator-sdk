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

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.DefaultInformerPool;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.InformerClassifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pooled informer is reference counted, so releasing the same namespace twice consumes a
 * reference another controller still holds. {@link InformerManager#stop()} and {@link
 * InformerManager#changeNamespaces(Set)} can run concurrently, so removing the source from the
 * manager has to be what claims the right to release it.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class InformerManagerConcurrentReleaseTest {

  private static final String NAMESPACE = "ns1";

  private final KubernetesClient clientMock = MockKubernetesClient.client(Deployment.class);
  private final LatchingInformerPool pool = new LatchingInformerPool();
  private final InformerEventSourceConfiguration<Deployment> configuration =
      mock(InformerEventSourceConfiguration.class);
  private final ResourceEventHandler<Deployment> eventHandler = mock(ResourceEventHandler.class);

  @BeforeEach
  void setup() {
    final var informerConfig = mock(InformerConfiguration.class);
    when(informerConfig.getEffectiveNamespaces(any())).thenReturn(Set.of(NAMESPACE));
    when(informerConfig.getInformerListLimit()).thenReturn(null);
    when(configuration.getInformerConfig()).thenReturn(informerConfig);
    when(configuration.getResourceClass()).thenReturn(Deployment.class);
  }

  @Test
  void concurrentStopAndNamespaceChangeReleaseTheInformerOnlyOnce() throws Exception {
    var manager =
        new InformerManager<Deployment, InformerEventSourceConfiguration<Deployment>>(
            configuration, eventHandler);
    manager.setControllerConfiguration(controllerConfiguration());
    manager.start();
    // a second controller shares the very same informer and never releases it, so the pool has to
    // keep it running no matter how the manager below is torn down
    var informer = pool.getInformer("other-controller", "other-es", classifier());

    // drop the only watched namespace on one thread; the pool blocks inside releaseInformer, which
    // is the window in which stop() used to see the already-released source and release it again
    var namespaceChange = new Thread(() -> manager.changeNamespaces(Set.of()));
    namespaceChange.start();
    assertThat(pool.enteredRelease.await(5, TimeUnit.SECONDS)).isTrue();

    manager.stop();

    pool.proceed.countDown();
    namespaceChange.join(TimeUnit.SECONDS.toMillis(5));

    assertThat(pool.releaseCount.get())
        .as("the same namespace must not be released twice")
        .isEqualTo(1);
    verify(informer, never()).stop();
  }

  private ControllerConfiguration<Deployment> controllerConfiguration() {
    ConfigurationService configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withKubernetesClient(clientMock).withInformerPool(pool));
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(controllerConfiguration.getName()).thenReturn("controller");
    return controllerConfiguration;
  }

  /** Has to match what the manager builds for {@link #NAMESPACE} so it hits the same pool entry. */
  private InformerClassifier<Deployment> classifier() {
    return new InformerClassifier<>(
        clientMock, null, null, NAMESPACE, Deployment.class, null, null, null, null);
  }

  /** Blocks inside the first release so the two teardown paths can be interleaved on purpose. */
  private static class LatchingInformerPool extends DefaultInformerPool {

    private final CountDownLatch enteredRelease = new CountDownLatch(1);
    private final CountDownLatch proceed = new CountDownLatch(1);
    private final AtomicInteger releaseCount = new AtomicInteger();
    private final AtomicBoolean blockNextRelease = new AtomicBoolean(true);

    @Override
    public <R extends HasMetadata> Optional<SharedIndexInformer<R>> releaseInformer(
        String controllerName, String name, InformerClassifier<R> classifier) {
      releaseCount.incrementAndGet();
      // deliberately blocking before delegating: releaseInformer is synchronized, so waiting inside
      // it would just serialize the two threads instead of interleaving them
      if (blockNextRelease.compareAndSet(true, false)) {
        enteredRelease.countDown();
        try {
          proceed.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return super.releaseInformer(controllerName, name, classifier);
    }
  }
}
