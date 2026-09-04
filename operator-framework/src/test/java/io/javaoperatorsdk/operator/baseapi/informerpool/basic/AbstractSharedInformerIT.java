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
package io.javaoperatorsdk.operator.baseapi.informerpool.basic;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.AbstractInformerPool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Registers two controllers, each backed by its own primary custom resource, that both watch {@link
 * ConfigMap} as a secondary resource using an identical {@code InformerEventSource} configuration.
 *
 * <p>The functional behavior (both controllers reconcile) is identical regardless of the informer
 * pool strategy; only the number of underlying {@code ConfigMap} informers differs, which is why
 * the expected count is left abstract. Concrete subclasses pick the pool strategy via {@link
 * #configurationServiceOverrider()}.
 */
public abstract class AbstractSharedInformerIT {

  public static final String TEST_RESOURCE_1 = "test1";
  public static final String TEST_RESOURCE_2 = "test2";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SharedInformerReconciler1())
          .withReconciler(new SharedInformerReconciler2())
          .withConfigurationService(configurationServiceOverrider())
          .build();

  /** The informer pool strategy under test. */
  protected abstract Consumer<ConfigurationServiceOverrider> configurationServiceOverrider();

  /**
   * Expected number of {@code ConfigMap} informers: {@code 1} when the two controllers share a
   * single informer, {@code 2} when each controller gets its own.
   */
  protected abstract long expectedConfigMapInformerCount();

  @Test
  void bothControllersReconcileWatchingConfigMap() {
    extension.create(customResource1(TEST_RESOURCE_1));
    extension.create(customResource2(TEST_RESOURCE_2));

    // both controllers reconcile, which guarantees their event sources (and thus their informers)
    // have been started
    await()
        .untilAsserted(
            () -> {
              assertThat(
                      extension
                          .getReconcilerOfType(SharedInformerReconciler1.class)
                          .getNumberOfExecutions())
                  .isPositive();
              assertThat(
                      extension
                          .getReconcilerOfType(SharedInformerReconciler2.class)
                          .getNumberOfExecutions())
                  .isPositive();
            });

    var pool =
        (AbstractInformerPool) extension.getOperator().getConfigurationService().informerPool();

    // the ConfigMap informer count depends on the pool strategy (shared vs. one-per-controller)
    assertThat(pool.numberOfInformersForResource(ConfigMap.class))
        .isEqualTo(expectedConfigMapInformerCount());
    // the two distinct primary resources are always backed by their own informers
    assertThat(pool.numberOfInformersForResource(SharedInformerCustomResource1.class)).isEqualTo(1);
    assertThat(pool.numberOfInformersForResource(SharedInformerCustomResource2.class)).isEqualTo(1);
  }

  SharedInformerCustomResource1 customResource1(String name) {
    var res = new SharedInformerCustomResource1();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return res;
  }

  SharedInformerCustomResource2 customResource2(String name) {
    var res = new SharedInformerCustomResource2();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return res;
  }
}
