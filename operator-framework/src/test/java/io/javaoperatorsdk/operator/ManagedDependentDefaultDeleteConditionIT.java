package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.maxintervalafterretry.MaxIntervalAfterRetryTestReconciler;

public class ManagedDependentDefaultDeleteConditionIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MaxIntervalAfterRetryTestReconciler()).build();

}
