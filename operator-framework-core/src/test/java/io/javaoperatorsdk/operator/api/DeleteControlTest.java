package io.javaoperatorsdk.operator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeleteControlTest {

  @Test
  void cannotReScheduleForDefaultDelete() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      DeleteControl.defaultDelete().withReSchedule(1000L);
    });
  }

}
