package io.javaoperatorsdk.operator.processing.event.source.filter;

@FunctionalInterface
public interface OnUpdateFilter<R> {

  boolean accept(R newResource, R oldResource);

  default OnUpdateFilter<R> and(OnUpdateFilter<R> onUpdateFilter) {
    return (newResource, oldResource) ->
        this.accept(newResource, oldResource) && onUpdateFilter.accept(newResource, oldResource);
  }

  default OnUpdateFilter<R> or(OnUpdateFilter<R> onUpdateFilter) {
    return (newResource, oldResource) ->
        this.accept(newResource, oldResource) || onUpdateFilter.accept(newResource, oldResource);
  }

  default OnUpdateFilter<R> not() {
    return (newResource, oldResource) -> !this.accept(newResource, oldResource);
  }
}
