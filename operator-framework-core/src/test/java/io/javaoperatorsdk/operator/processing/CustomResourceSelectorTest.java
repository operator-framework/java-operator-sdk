package io.javaoperatorsdk.operator.processing;

import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.Metrics;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.TimerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomResourceSelectorTest {

  public static final int FAKE_CONTROLLER_EXECUTION_DURATION = 250;
  public static final int SEPARATE_EXECUTION_TIMEOUT = 450;

  private final EventDispatcher eventDispatcherMock = mock(EventDispatcher.class);
  private final CustomResourceCache customResourceCache = new CustomResourceCache();

  private final DefaultEventSourceManager defaultEventSourceManagerMock =
      mock(DefaultEventSourceManager.class);

  private TimerEventSource retryTimerEventSourceMock = mock(TimerEventSource.class);
  private ControllerConfiguration configuration =
      mock(ControllerConfiguration.class);
  private final ConfigurationService configService = mock(ConfigurationService.class);

  private final DefaultEventHandler defaultEventHandler =
      new DefaultEventHandler(
          eventDispatcherMock,
          "Test",
          null,
          ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER, configuration);

  @BeforeEach
  public void setup() {
    defaultEventHandler.setEventSourceManager(defaultEventSourceManagerMock);

    // todo: remove
    when(defaultEventSourceManagerMock.getCache()).thenReturn(customResourceCache);
    doCallRealMethod().when(defaultEventSourceManagerMock).getLatestResource(any());
    doCallRealMethod().when(defaultEventSourceManagerMock).getLatestResources(any());
    doCallRealMethod().when(defaultEventSourceManagerMock).getLatestResourceUids(any());
    doCallRealMethod().when(defaultEventSourceManagerMock).cacheResource(any(), any());
    doAnswer(
        invocation -> {
          final var resourceId = (String) invocation.getArgument(0);
          customResourceCache.cleanup(resourceId);
          return null;
        })
            .when(defaultEventSourceManagerMock)
            .cleanup(any());

    when(configuration.getName()).thenReturn("DefaultEventHandlerTest");
    when(configService.getMetrics()).thenReturn(Metrics.NOOP);
    when(configuration.getConfigurationService()).thenReturn(configService);
  }

  @Test
  public void dispatchEventsWithPredicate() {
    TestCustomResource cr1 = testCustomResource(UUID.randomUUID().toString());
    cr1.getSpec().setValue("1");
    TestCustomResource cr2 = testCustomResource(UUID.randomUUID().toString());
    cr2.getSpec().setValue("2");
    TestCustomResource cr3 = testCustomResource(UUID.randomUUID().toString());
    cr3.getSpec().setValue("3");

    customResourceCache.cacheResource(cr1);
    customResourceCache.cacheResource(cr2);
    customResourceCache.cacheResource(cr3);

    defaultEventHandler.handleEvent(
        new DefaultEvent(
            c -> {
              var tcr = ((TestCustomResource) c);
              return Objects.equals("1", tcr.getSpec().getValue())
                  || Objects.equals("3", tcr.getSpec().getValue());
            },
            null));

    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(any());

    waitMinimalTime();

    ArgumentCaptor<ExecutionScope> executionScopeArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionScope.class);

    verify(eventDispatcherMock, timeout(SEPARATE_EXECUTION_TIMEOUT).times(2))
        .handleExecution(executionScopeArgumentCaptor.capture());

    assertThat(executionScopeArgumentCaptor.getAllValues())
        .hasSize(2)
        .allSatisfy(
            s -> {
              assertThat(s.getEvents()).isNotEmpty().hasOnlyElementsOfType(DefaultEvent.class);
              assertThat(s)
                  .satisfiesAnyOf(
                      e -> Objects.equals(cr1.getMetadata().getUid(), e.getCustomResourceUid()),
                      e -> Objects.equals(cr3.getMetadata().getUid(), e.getCustomResourceUid()));
            });
  }

  private void waitMinimalTime() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private CustomResourceEvent prepareCREvent() {
    return prepareCREvent(UUID.randomUUID().toString());
  }

  private CustomResourceEvent prepareCREvent(String uid) {
    TestCustomResource customResource = testCustomResource(uid);
    customResourceCache.cacheResource(customResource);
    return new CustomResourceEvent(Watcher.Action.MODIFIED, customResource, null);
  }
}
