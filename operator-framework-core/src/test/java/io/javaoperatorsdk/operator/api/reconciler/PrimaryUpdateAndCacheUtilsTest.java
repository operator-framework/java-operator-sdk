package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils.DEFAULT_MAX_RETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrimaryUpdateAndCacheUtilsTest {

  Context<TestCustomResource> context = mock(Context.class);
  KubernetesClient client = mock(KubernetesClient.class);
  Resource resource = mock(Resource.class);

  @BeforeEach
  void setup() {
    when(context.getClient()).thenReturn(client);
    var esr = mock(EventSourceRetriever.class);
    when(context.eventSourceRetriever()).thenReturn(esr);
    when(esr.getControllerEventSource()).thenReturn(mock(ControllerEventSource.class));
    var mixedOp = mock(MixedOperation.class);
    when(client.resources(any())).thenReturn(mixedOp);
    when(mixedOp.inNamespace(any())).thenReturn(mixedOp);
    when(mixedOp.withName(any())).thenReturn(resource);
    when(resource.get()).thenReturn(TestUtils.testCustomResource1());
  }

  @Test
  void handlesUpdate() {
    var updated =
        PrimaryUpdateAndCacheUtils.updateAndCacheResource(
            TestUtils.testCustomResource1(),
            context,
            r -> {
              var res = TestUtils.testCustomResource1();
              // setting this to null to test if value set in the implementation
              res.getMetadata().setResourceVersion(null);
              res.getSpec().setValue("updatedValue");
              return res;
            },
            r -> {
              // checks if the resource version is set from the original resource
              assertThat(r.getMetadata().getResourceVersion()).isEqualTo("1");
              var res = TestUtils.testCustomResource1();
              res.setSpec(r.getSpec());
              res.getMetadata().setResourceVersion("2");
              return res;
            });

    assertThat(updated.getMetadata().getResourceVersion()).isEqualTo("2");
    assertThat(updated.getSpec().getValue()).isEqualTo("updatedValue");
  }

  @Test
  void retriesConflicts() {
    var updateOperation = mock(UnaryOperator.class);

    when(updateOperation.apply(any()))
        .thenThrow(new KubernetesClientException("", 409, null))
        .thenReturn(TestUtils.testCustomResource1());

    var updated =
        PrimaryUpdateAndCacheUtils.updateAndCacheResource(
            TestUtils.testCustomResource1(),
            context,
            r -> {
              var res = TestUtils.testCustomResource1();
              res.getSpec().setValue("updatedValue");
              return res;
            },
            updateOperation);

    assertThat(updated).isNotNull();
    verify(resource, times(1)).get();
  }

  @Test
  void throwsIfRetryExhausted() {
    var updateOperation = mock(UnaryOperator.class);

    when(updateOperation.apply(any())).thenThrow(new KubernetesClientException("", 409, null));

    assertThrows(
        OperatorException.class,
        () ->
            PrimaryUpdateAndCacheUtils.updateAndCacheResource(
                TestUtils.testCustomResource1(),
                context,
                UnaryOperator.identity(),
                updateOperation));
    verify(resource, times(DEFAULT_MAX_RETRY)).get();
  }
}
