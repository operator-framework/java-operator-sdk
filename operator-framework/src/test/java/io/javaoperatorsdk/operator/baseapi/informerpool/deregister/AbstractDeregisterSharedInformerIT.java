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
package io.javaoperatorsdk.operator.baseapi.informerpool.deregister;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.AbstractInformerPool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the lifecycle of a dynamically registered event source in the informer pool: registering
 * it creates a (single) informer for the watched resource, and de-registering it releases that
 * informer so the pool no longer holds one for that resource type.
 *
 * <p>A single controller registers a single event source, so no informer sharing is involved: the
 * behavior and assertions are identical for every pool strategy. Concrete subclasses only pick the
 * strategy via {@link #configurationServiceOverrider()}.
 */
public abstract class AbstractDeregisterSharedInformerIT {

  private static final String PRIMARY_NAME = "primary1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withAdditionalCustomResourceDefinition(DeregisterWatchedCustomResource.class)
          .withReconciler(new DeregisterReconciler())
          .withConfigurationService(configurationServiceOverrider())
          .build();

  /** The informer pool strategy under test. */
  protected abstract Consumer<ConfigurationServiceOverrider> configurationServiceOverrider();

  @Test
  void deregisteringDynamicEventSourceRemovesInformerFromPool() {
    var reconciler = extension.getReconcilerOfType(DeregisterReconciler.class);
    var pool =
        (AbstractInformerPool) extension.getOperator().getConfigurationService().informerPool();

    // Create the primary with registration enabled: the reconciler dynamically registers the event
    // source for the watched resource.
    extension.create(primary(true));

    // The dynamically registered event source establishes exactly one informer for the watched
    // resource in the pool.
    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isPositive();
              assertThat(pool.numberOfInformersForResource(DeregisterWatchedCustomResource.class))
                  .isEqualTo(1);
            });

    var executionsBeforeDeregister = reconciler.getNumberOfExecutions();

    // Flip the spec so the next reconciliation de-registers the event source.
    var toUpdate = extension.get(DeregisterPrimaryCustomResource.class, PRIMARY_NAME);
    toUpdate.getSpec().setRegisterEventSource(false);
    extension.replace(toUpdate);

    // After the de-registration reconciliation runs, the informer is released from the pool.
    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions())
                  .isGreaterThan(executionsBeforeDeregister);
              assertThat(pool.numberOfInformersForResource(DeregisterWatchedCustomResource.class))
                  .isZero();
            });
  }

  DeregisterPrimaryCustomResource primary(boolean registerEventSource) {
    var res = new DeregisterPrimaryCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(PRIMARY_NAME).build());
    res.setSpec(new DeregisterSpec().setRegisterEventSource(registerEventSource));
    return res;
  }
}
