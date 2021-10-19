package io.javaoperatorsdk.operator;

public interface VersionLogger {
  VersionLogger NOOP = new VersionLogger() {
    @Override
    public void start() {}

    @Override
    public void close() {}
  };

  void start();

  void close();
}
