package io.javaoperatorsdk.operator.api.config.loader;

import java.util.Optional;

public class DefatulConfigProvider implements ConfigProvider {
  @Override
  public <T> Optional<T> getValue(String key, Class<T> type) {

    return Optional.empty();
  }
}
