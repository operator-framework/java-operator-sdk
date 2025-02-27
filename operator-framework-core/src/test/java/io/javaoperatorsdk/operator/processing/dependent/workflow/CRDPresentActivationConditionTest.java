package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class CRDPresentActivationConditionTest {

  public static final int TEST_CHECK_INTERVAL = 50;
  public static final int TEST_CHECK_INTERVAL_WITH_SLACK = TEST_CHECK_INTERVAL + 10;
  private final CRDPresentActivationCondition.CRDPresentChecker checkerMock =
      mock(CRDPresentActivationCondition.CRDPresentChecker.class);
  private final CRDPresentActivationCondition condition =
      new CRDPresentActivationCondition(checkerMock, 2, Duration.ofMillis(TEST_CHECK_INTERVAL));
  private final DependentResource<TestCustomResource, TestCustomResource> dr =
      mock(DependentResource.class);
  private final Context context = mock(Context.class);

  @BeforeEach
  void setup() {
    CRDPresentActivationCondition.clearState();
    when(checkerMock.checkIfCRDPresent(any(), any())).thenReturn(false);
    when(dr.resourceType()).thenReturn(TestCustomResource.class);
  }

  @Test
  void checkCRDIfNotCheckedBefore() {
    when(checkerMock.checkIfCRDPresent(any(), any())).thenReturn(true);

    assertThat(condition.isMet(dr, null, context)).isTrue();
    verify(checkerMock, times(1)).checkIfCRDPresent(any(), any());
  }

  @Test
  void instantMetCallSkipsApiCall() {
    condition.isMet(dr, null, context);
    verify(checkerMock, times(1)).checkIfCRDPresent(any(), any());

    condition.isMet(dr, null, context);
    verify(checkerMock, times(1)).checkIfCRDPresent(any(), any());
  }

  @Test
  void intervalExpiredAPICheckedAgain() throws InterruptedException {
    condition.isMet(dr, null, context);
    verify(checkerMock, times(1)).checkIfCRDPresent(any(), any());

    Thread.sleep(TEST_CHECK_INTERVAL_WITH_SLACK);

    condition.isMet(dr, null, context);
    verify(checkerMock, times(2)).checkIfCRDPresent(any(), any());
  }

  @Test
  void crdIsNotCheckedAnymoreIfIfOnceFound() throws InterruptedException {
    when(checkerMock.checkIfCRDPresent(any(), any())).thenReturn(true);

    condition.isMet(dr, null, context);
    verify(checkerMock, times(1)).checkIfCRDPresent(any(), any());

    Thread.sleep(TEST_CHECK_INTERVAL_WITH_SLACK);

    condition.isMet(dr, null, context);
    verify(checkerMock, times(1)).checkIfCRDPresent(any(), any());
  }

  @Test
  void crdNotCheckedAnymoreIfCountExpires() throws InterruptedException {
    condition.isMet(dr, null, context);
    Thread.sleep(TEST_CHECK_INTERVAL_WITH_SLACK);
    condition.isMet(dr, null, context);
    Thread.sleep(TEST_CHECK_INTERVAL_WITH_SLACK);
    condition.isMet(dr, null, context);

    verify(checkerMock, times(2)).checkIfCRDPresent(any(), any());
  }
}
