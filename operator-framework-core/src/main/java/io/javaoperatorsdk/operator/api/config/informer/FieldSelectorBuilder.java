package io.javaoperatorsdk.operator.api.config.informer;

import java.util.ArrayList;
import java.util.List;

public class FieldSelectorBuilder {

  private final List<FieldSelector.Field> fields = new ArrayList<>();

  public FieldSelectorBuilder withField(String path, String value) {
    fields.add(new FieldSelector.Field(path, value));
    return this;
  }

  public FieldSelectorBuilder withoutField(String path, String value) {
    fields.add(new FieldSelector.Field(path, value, true));
    return this;
  }

  public FieldSelector build() {
    return new FieldSelector(fields);
  }
}
