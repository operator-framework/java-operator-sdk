package io.javaoperatorsdk.operator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DeleteControlTest {

  @Test
  void cannotReScheduleForDefaultDelete() {
    Assertions.assertThrows(IllegalStateException.class,
        () -> DeleteControl.defaultDelete().rescheduleAfter(1000L));
  }

}
