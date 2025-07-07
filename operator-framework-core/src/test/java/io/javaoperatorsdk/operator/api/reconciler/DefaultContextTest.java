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
      new DefaultContext<>(null, mockController, primary, null);

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
