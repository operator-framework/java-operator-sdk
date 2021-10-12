package io.javaoperatorsdk.operator.processing.event.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabelSelectorParserTest {

  @Test
  public void nullParamReturnsEmptyMap() {
    var res = LabelSelectorParser.parseSimpleLabelSelector(null);
    assertThat(res).hasSize(0);
  }

  @Test
  public void emptyLabelSelectorReturnsEmptyMap() {
    var res = LabelSelectorParser.parseSimpleLabelSelector(" ");
    assertThat(res).hasSize(0);
  }

  @Test
  public void parseSimpleLabelSelector() {
    var res = LabelSelectorParser.parseSimpleLabelSelector("app=foo");
    assertThat(res).hasSize(1).containsEntry("app", "foo");

    res = LabelSelectorParser.parseSimpleLabelSelector("app=foo,owner=me");
    assertThat(res).hasSize(2).containsEntry("app", "foo").containsEntry("owner", "me");
  }
}
