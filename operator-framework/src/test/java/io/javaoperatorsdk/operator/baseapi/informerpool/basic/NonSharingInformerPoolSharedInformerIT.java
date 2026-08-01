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

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.processing.event.source.informer.pool.NonSharingInformerPool;

/**
 * Runs {@link AbstractSharedInformerIT} with the {@link NonSharingInformerPool}: informers are
 * never shared, so each of the two controllers watching {@code ConfigMap} gets its own informer.
 */
public class NonSharingInformerPoolSharedInformerIT extends AbstractSharedInformerIT {

  @Override
  protected Consumer<ConfigurationServiceOverrider> configurationServiceOverrider() {
    return overrider -> overrider.withInformerPool(new NonSharingInformerPool());
  }

  @Override
  protected long expectedConfigMapInformerCount() {
    // no sharing: one ConfigMap informer per controller
    return 2;
  }
}
