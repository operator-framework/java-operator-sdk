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
package io.javaoperatorsdk.operator.api.reconciler;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.NoEventSourceForClassException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultContextTest {

  private final Secret primary = new Secret();
  private final Controller<Secret> mockController = mock();

  private final DefaultContext<?> context =
      new DefaultContext<>(null, mockController, primary, false, false);

  @Test
  @SuppressWarnings("unchecked")
  void getSecondaryResourceReturnsEmptyOptionalOnNonActivatedDRType() {
    var mockManager = mock(EventSourceManager.class);
    when(mockController.getEventSourceManager()).thenReturn(mockManager);
    when(mockController.workflowContainsDependentForType(ConfigMap.class)).thenReturn(true);
    when(mockManager.getEventSourceFor(any(), any()))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));

    var res = context.getSecondaryResource(ConfigMap.class);
    assertThat(res).isEmpty();
  }

  @Test
  void setRetryInfo() {
    RetryInfo retryInfo = mock();
    var newContext = context.setRetryInfo(retryInfo);
    assertThat(newContext).isSameAs(context);
    assertThat(newContext.getRetryInfo()).hasValue(retryInfo);
  }
}
