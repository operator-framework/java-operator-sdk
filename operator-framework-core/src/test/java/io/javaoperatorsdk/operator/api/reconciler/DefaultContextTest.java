package io.javaoperatorsdk.operator.api.reconciler;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.NoEventSourceForClassException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultContextTest {

  Secret primary = new Secret();
  Controller<Secret> mockController = mock(Controller.class);

  DefaultContext<?> context = new DefaultContext<>(null, mockController, primary);

  @Test
  void getSecondaryResourceReturnsEmptyOptionalOnNonActivatedDRType() {
    var mockManager = mock(EventSourceManager.class);
    when(mockController.getEventSourceManager()).thenReturn(mockManager);
    when(mockController.workflowContainsDependentForType(ConfigMap.class)).thenReturn(true);
    when(mockManager.getEventSourceFor(any(), any()))
        .thenThrow(new NoEventSourceForClassException(ConfigMap.class));

    var res = context.getSecondaryResource(ConfigMap.class);

    assertThat(res).isEmpty();
  }

}
