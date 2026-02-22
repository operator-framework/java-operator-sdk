package io.javaoperatorsdk.operator.api.config.loader;

import java.util.Optional;

public interface ConfigProvider {

  <T> Optional<T> getConfig(String key, Class<T> type);
}
