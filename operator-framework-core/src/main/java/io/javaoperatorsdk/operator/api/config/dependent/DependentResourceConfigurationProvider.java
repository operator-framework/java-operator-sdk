package io.javaoperatorsdk.operator.api.config.dependent;

import java.util.Optional;

public interface DependentResourceConfigurationProvider {
  Optional<Object> configurationFor(DependentResourceSpec<?, ?> dependent);
}
