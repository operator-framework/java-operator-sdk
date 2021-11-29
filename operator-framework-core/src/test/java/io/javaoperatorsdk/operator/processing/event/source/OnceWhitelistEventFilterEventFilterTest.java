package io.javaoperatorsdk.operator.processing.event.source;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.OnceWhitelistEventFilterEventFilter;

import static io.javaoperatorsdk.operator.TestUtils.testCustomResource;
import static org.assertj.core.api.Assertions.assertThat;

class OnceWhitelistEventFilterEventFilterTest {

  private OnceWhitelistEventFilterEventFilter filter = new OnceWhitelistEventFilterEventFilter<>();

  @Test
  public void notAcceptCustomResourceNotWhitelisted() {
    assertThat(filter.acceptChange(null,
        testCustomResource(), testCustomResource())).isFalse();
  }

  @Test
  public void allowCustomResourceWhitelisted() {
    var cr = TestUtils.testCustomResource();

    filter.whitelistNextEvent(ResourceID.fromResource(cr));

    assertThat(filter.acceptChange(null,
        cr, cr)).isTrue();
  }

  @Test
  public void allowCustomResourceWhitelistedOnlyOnce() {
    var cr = TestUtils.testCustomResource();

    filter.whitelistNextEvent(ResourceID.fromResource(cr));

    assertThat(filter.acceptChange(null,
        cr, cr)).isTrue();
    assertThat(filter.acceptChange(null,
        cr, cr)).isFalse();
  }

}
