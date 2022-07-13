package io.javaoperatorsdk.operator.processing.event.source.filter;

@FunctionalInterface
public interface OnAddFilter<R> {
  boolean accept(R resource);

  default OnAddFilter<R> and(OnAddFilter<R> onAddFilter) {
    return (resource) -> this.accept(resource) && onAddFilter.accept(resource);
  }

  default OnAddFilter<R> or(OnAddFilter<R> onAddFilter) {
    return (resource) -> this.accept(resource) || onAddFilter.accept(resource);
  }

  default OnAddFilter<R> not() {
    return (resource) -> !this.accept(resource);
  }
}
