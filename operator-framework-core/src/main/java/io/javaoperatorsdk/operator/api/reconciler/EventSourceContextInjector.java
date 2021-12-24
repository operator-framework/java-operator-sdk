package io.javaoperatorsdk.operator.api.reconciler;

public interface EventSourceContextInjector {
  void injectInto(EventSourceContext context);
}
