package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;

import static io.javaoperatorsdk.operator.TestUtils.markForDeletion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class InternalEventFiltersTest {

  public static final String FINALIZER = "finalizer";

  @Test
  void onUpdateMarkedForDeletion() {
    var res = markForDeletion(TestUtils.testCustomResource());
    assertThat(InternalEventFilters.onUpdateMarkedForDeletion().accept(res, res)).isTrue();
  }

  @Test
  void generationAware() {
    var res = TestUtils.testCustomResource1();
    var res2 = TestUtils.testCustomResource1();
    res2.getMetadata().setGeneration(2L);

    assertThat(InternalEventFilters.onUpdateGenerationAware(true).accept(res2, res)).isTrue();
    assertThat(InternalEventFilters.onUpdateGenerationAware(true).accept(res, res)).isFalse();
    assertThat(InternalEventFilters.onUpdateGenerationAware(false).accept(res, res)).isTrue();
  }

  @Test
  void finalizerCheckedIfConfigured() {
    assertThat(InternalEventFilters.onUpdateFinalizerNeededAndApplied(true, FINALIZER)
        .accept(TestUtils.testCustomResource1(), TestUtils.testCustomResource1())).isTrue();

    var res = TestUtils.testCustomResource1();
    res.getMetadata().setFinalizers(List.of(FINALIZER));

    assertThat(InternalEventFilters.onUpdateFinalizerNeededAndApplied(true, FINALIZER)
        .accept(res, res)).isFalse();
  }

  @Test
  void acceptsIfFinalizerWasJustAdded() {
    var res = TestUtils.testCustomResource1();
    res.getMetadata().setFinalizers(List.of(FINALIZER));

    assertThat(InternalEventFilters.onUpdateFinalizerNeededAndApplied(true, "finalizer")
        .accept(res, TestUtils.testCustomResource1())).isTrue();
  }

  @Test
  void dontAcceptIfFinalizerNotUsed() {
    assertThat(InternalEventFilters.onUpdateFinalizerNeededAndApplied(false, FINALIZER)
        .accept(TestUtils.testCustomResource1(), TestUtils.testCustomResource1())).isFalse();
  }
}
