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

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;

/** Runs {@link AbstractDeregisterSharedInformerIT} with the default (sharing) informer pool. */
class DeregisterSharedInformerIT extends AbstractDeregisterSharedInformerIT {

  @Override
  protected Consumer<ConfigurationServiceOverrider> configurationServiceOverrider() {
    return overrider -> {};
  }
}
