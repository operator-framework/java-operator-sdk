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
package io.javaoperatorsdk.operator.junit;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LocallyRunOperatorExtensionTest {

  @Test
  void doesNotEnableVirtualThreadsWhenNotRequested() {
    var builder = builder();
    builder.useVirtualThreads = false;

    assertThat(configurationService(builder))
        .returns(false, ConfigurationService::useVirtualThreads);
  }

  @Test
  void virtualThreadsCanBeEnabledForTheWholeTestSuite() {
    var builder = builder();
    builder.useVirtualThreads = true;

    assertThat(configurationService(builder))
        .returns(true, ConfigurationService::useVirtualThreads);
  }

  @Test
  void explicitConfigurationTakesPrecedenceOverTestSuiteWideVirtualThreadsSetting() {
    var builder = builder().withConfigurationService(o -> o.withUseVirtualThreads(false));
    builder.useVirtualThreads = true;

    assertThat(configurationService(builder))
        .returns(false, ConfigurationService::useVirtualThreads);
  }

  private static LocallyRunOperatorExtension.Builder builder() {
    return LocallyRunOperatorExtension.builder().withKubernetesClient(mock(KubernetesClient.class));
  }

  private static ConfigurationService configurationService(
      LocallyRunOperatorExtension.Builder builder) {
    return builder.build().getOperator().getConfigurationService();
  }
}
