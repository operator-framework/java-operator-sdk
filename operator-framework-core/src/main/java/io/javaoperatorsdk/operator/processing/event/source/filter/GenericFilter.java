package io.javaoperatorsdk.operator.processing.event.source.filter;

@FunctionalInterface
public interface GenericFilter<R> {

  boolean accept(R resource);

  default GenericFilter<R> and(GenericFilter<R> genericFilter) {
    return (resource) -> this.accept(resource) && genericFilter.accept(resource);
  }

  default GenericFilter<R> or(GenericFilter<R> genericFilter) {
    return (resource) -> this.accept(resource) || genericFilter.accept(resource);
  }

  default GenericFilter<R> not() {
    return (resource) -> !this.accept(resource);
  }
}
