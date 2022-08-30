package io.javaoperatorsdk.operator.processing.dependent;

import io.javaoperatorsdk.operator.processing.event.source.Cache;

public interface CacheBacked<R, C extends Cache<R>> {
  C cache();
}
