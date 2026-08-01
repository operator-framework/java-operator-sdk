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

import java.util.function.Consumer;

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.NonSharingInformerPool;

/**
 * Runs {@link AbstractDynamicSharedInformerIT} with the {@link NonSharingInformerPool}: the static
 * and dynamic reconcilers each get their own informer for the third resource. The dynamic
 * reconciler is still triggered for the pre-existing third resource, because its own (newly
 * created) informer replays the cache to the handler once it syncs.
 */
class NonSharingInformerPoolDynamicSharedInformerIT extends AbstractDynamicSharedInformerIT {

  @Override
  protected Consumer<ConfigurationServiceOverrider> configurationServiceOverrider() {
    return overrider -> overrider.withInformerPool(new NonSharingInformerPool());
  }

  @Override
  protected long expectedThirdResourceInformerCount() {
    // no sharing: one informer for the static reconciler and one for the dynamic reconciler
    return 2;
  }
}
