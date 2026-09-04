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
package io.javaoperatorsdk.operator.baseapi.informerpool.dynamic;

import java.time.Duration;
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
 * Two reconcilers watch the same "third" custom resource ({@link
 * DynamicSharedInformerThirdCustomResource}) as a secondary resource, but register their informer
 * event sources differently:
 *
 * <ul>
 *   <li>{@link StaticSharedInformerReconciler} registers it statically at startup, so its handler
 *       is present on the underlying informer before any third resource is created.
 *   <li>{@link DynamicSharedInformerReconciler} registers it dynamically from within {@code
 *       reconcile}, i.e. after its informer is already running (and already has the pre-existing
 *       third resource in its cache).
 * </ul>
 *
 * <p>Regardless of the pool strategy, the dynamically registered event source must be triggered for
 * the pre-existing third resource: on registration the framework replays the resources already in
 * the running informer's cache to the newly added handler, which maps the third resource back to
 * the dynamic reconciler's primary.
 *
 * <p>What differs by strategy is the number of underlying informers for the third resource: a
 * sharing pool establishes a single informer used by both reconcilers, whereas a non-sharing pool
 * creates one per reconciler. That expected count is left abstract; concrete subclasses pick the
 * strategy via {@link #configurationServiceOverrider()}.
 */
public abstract class AbstractDynamicSharedInformerIT {

  private static final String THIRD_RESOURCE_NAME = "third1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withAdditionalCustomResourceDefinition(DynamicSharedInformerThirdCustomResource.class)
          .withReconciler(new StaticSharedInformerReconciler())
          .withReconciler(new DynamicSharedInformerReconciler())
          .withConfigurationService(configurationServiceOverrider())
          .build();

  /** The informer pool strategy under test. */
  protected abstract Consumer<ConfigurationServiceOverrider> configurationServiceOverrider();

  /**
   * Expected number of informers for the third resource once both reconcilers watch it: {@code 1}
   * when the informer is shared, {@code 2} when each reconciler gets its own.
   */
  protected abstract long expectedThirdResourceInformerCount();

  @Test
  void dynamicallyRegisteredEventSourceReceivesInitialEvent() {
    var staticReconciler = extension.getReconcilerOfType(StaticSharedInformerReconciler.class);
    var dynamicReconciler = extension.getReconcilerOfType(DynamicSharedInformerReconciler.class);

    // The static reconciler's primary must exist so that third-resource events (which map to it)
    // actually result in a reconciliation.
    extension.create(primary1());
    // The third resource is created before the dynamic event source is registered, so it is a
    // pre-existing resource from the perspective of the dynamically added handler.
    extension.create(thirdResource());

    // Sanity check that the informer machinery works at all: the static reconciler, whose handler
    // was present from startup, is triggered by the (live) creation of the third resource.
    await().untilAsserted(() -> assertThat(staticReconciler.getNumberOfExecutions()).isPositive());

    // Creating the second primary triggers the dynamic reconciler, which registers its own event
    // source for the third resource against an already-running informer.
    extension.create(primary2());
    await().untilAsserted(() -> assertThat(dynamicReconciler.getNumberOfExecutions()).isPositive());

    // (1) Informer count for the third resource, which depends on the pool strategy.
    var pool =
        (AbstractInformerPool) extension.getOperator().getConfigurationService().informerPool();
    await()
        .untilAsserted(
            () ->
                assertThat(
                        pool.numberOfInformersForResource(
                            DynamicSharedInformerThirdCustomResource.class))
                    .isEqualTo(expectedThirdResourceInformerCount()));

    // (2) The dynamically registered event source now watches the pre-existing third resource. On
    // registration the framework replays the resources already in the running informer's cache to
    // the newly added handler, which maps the third resource back to the dynamic reconciler's
    // primary. This triggers a second reconciliation, in addition to the first one (the primary2
    // creation) that performed the registration. Without the replay the dynamic reconciler would
    // only ever run once.
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> assertThat(dynamicReconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(2));
  }

  DynamicSharedInformerPrimaryCustomResource1 primary1() {
    var res = new DynamicSharedInformerPrimaryCustomResource1();
    res.setMetadata(
        new ObjectMetaBuilder().withName(StaticSharedInformerReconciler.PRIMARY_NAME).build());
    return res;
  }

  DynamicSharedInformerPrimaryCustomResource2 primary2() {
    var res = new DynamicSharedInformerPrimaryCustomResource2();
    res.setMetadata(
        new ObjectMetaBuilder().withName(DynamicSharedInformerReconciler.PRIMARY_NAME).build());
    return res;
  }

  DynamicSharedInformerThirdCustomResource thirdResource() {
    var res = new DynamicSharedInformerThirdCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(THIRD_RESOURCE_NAME).build());
    return res;
  }
}
