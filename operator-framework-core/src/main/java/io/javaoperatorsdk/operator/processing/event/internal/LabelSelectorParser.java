package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LabelSelectorParser {

  public static Map<String, String> parseSimpleLabelSelector(String labelSelector) {
    if (labelSelector == null || labelSelector.isBlank()) {
      return Collections.EMPTY_MAP;
    }
    String[] selectors = labelSelector.split(",");
    Map<String,String> labels = new HashMap<>(selectors.length);
    Arrays.stream(selectors).forEach(s -> {
      var kv = s.split("=");
      labels.put(kv[0],kv[1]);
    });
    return labels;
  }

}
