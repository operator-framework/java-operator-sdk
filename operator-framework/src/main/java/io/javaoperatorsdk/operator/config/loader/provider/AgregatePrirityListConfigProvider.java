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
package io.javaoperatorsdk.operator.config.loader.provider;

import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.config.loader.ConfigProvider;

/**
 * A {@link ConfigProvider} that delegates to an ordered list of providers. Providers are queried in
 * list order; the first non-empty result wins.
 */
public class AgregatePrirityListConfigProvider implements ConfigProvider {

  private final List<ConfigProvider> providers;

  public AgregatePrirityListConfigProvider(List<ConfigProvider> providers) {
    this.providers = List.copyOf(providers);
  }

  @Override
  public <T> Optional<T> getValue(String key, Class<T> type) {
    for (ConfigProvider provider : providers) {
      Optional<T> value = provider.getValue(key, type);
      if (value.isPresent()) {
        return value;
      }
    }
    return Optional.empty();
  }
}
