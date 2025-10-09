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
package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockControllerConfiguration {

  public static <R extends HasMetadata> ControllerConfiguration<R> forResource(
      Class<R> resourceType) {
    return forResource(resourceType, null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <R extends HasMetadata> ControllerConfiguration<R> forResource(
      Class<R> resourceType, ConfigurationService configurationService) {
    final ControllerConfiguration configuration = mock(ControllerConfiguration.class);
    final InformerConfiguration informerConfiguration = mock(InformerConfiguration.class);
    when(configuration.getInformerConfig()).thenReturn(informerConfiguration);
    when(configuration.getResourceClass()).thenReturn(resourceType);
    when(informerConfiguration.getNamespaces()).thenReturn(DEFAULT_NAMESPACES_SET);
    when(informerConfiguration.getEffectiveNamespaces(any())).thenCallRealMethod();
    when(configuration.getName()).thenReturn(resourceType.getSimpleName());
    when(configuration.getConfigurationService()).thenReturn(configurationService);
    return configuration;
  }
}
