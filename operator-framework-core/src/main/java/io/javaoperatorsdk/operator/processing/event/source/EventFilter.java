package io.javaoperatorsdk.operator.processing.event.source;

public interface EventFilter<T> {

  default boolean acceptAdd(T newResource) {
    return true;
  }

  default boolean acceptUpdate(T newResource, T oldResource) {
    return true;
  }

  default boolean acceptDelete(T resource, boolean deletedFinalStateUnknown) {
    return true;
  }

  default EventFilter<T> or(EventFilter<T> eventFilter) {
    return new EventFilter<>() {
      @Override
      public boolean acceptAdd(T newResource) {
        return EventFilter.this.acceptAdd(newResource) || eventFilter.acceptAdd(newResource);
      }

      @Override
      public boolean acceptUpdate(T newResource, T oldResource) {
        return EventFilter.this.acceptUpdate(newResource, oldResource) ||
            eventFilter.acceptUpdate(newResource, oldResource);
      }

      @Override
      public boolean acceptDelete(T resource, boolean deletedFinalStateUnknown) {
        return EventFilter.this.acceptDelete(resource, deletedFinalStateUnknown) ||
            eventFilter.acceptDelete(resource, deletedFinalStateUnknown);
      }
    };
  }

  default EventFilter<T> and(EventFilter<T> eventFilter) {
    return new EventFilter<>() {
      @Override
      public boolean acceptAdd(T newResource) {
        return EventFilter.this.acceptAdd(newResource) && eventFilter.acceptAdd(newResource);
      }

      @Override
      public boolean acceptUpdate(T newResource, T oldResource) {
        return EventFilter.this.acceptUpdate(newResource, oldResource) &&
            eventFilter.acceptUpdate(newResource, oldResource);
      }

      @Override
      public boolean acceptDelete(T resource, boolean deletedFinalStateUnknown) {
        return EventFilter.this.acceptDelete(resource, deletedFinalStateUnknown) &&
            eventFilter.acceptDelete(resource, deletedFinalStateUnknown);
      }
    };
  }

}
