package io.javaoperatorsdk.operator.processing.event.source.filter;

@FunctionalInterface
public interface OnDeleteFilter<R> {

  boolean accept(R hasMetadata, Boolean deletedFinalStateUnknown);

  default OnDeleteFilter<R> and(OnDeleteFilter<R> OnDeleteFilter) {
    return (resource, deletedFinalStateUnknown) ->
        this.accept(resource, deletedFinalStateUnknown)
            && OnDeleteFilter.accept(resource, deletedFinalStateUnknown);
  }

  default OnDeleteFilter<R> or(OnDeleteFilter<R> OnDeleteFilter) {
    return (resource, deletedFinalStateUnknown) ->
        this.accept(resource, deletedFinalStateUnknown)
            || OnDeleteFilter.accept(resource, deletedFinalStateUnknown);
  }

  default OnDeleteFilter<R> not() {
    return (resource, deletedFinalStateUnknown) -> !this.accept(resource, deletedFinalStateUnknown);
  }
}
