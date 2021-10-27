package io.javaoperatorsdk.operator.processing.event;

import java.io.Closeable;
import java.io.IOException;

public interface EventHandler extends Closeable {

  void handleEvent(Event event);

  @Override
  default void close() throws IOException {}

  default void start() {}
}
