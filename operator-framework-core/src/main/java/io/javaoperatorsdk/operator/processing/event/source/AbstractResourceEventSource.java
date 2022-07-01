package io.javaoperatorsdk.operator.processing.event.source;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;

public abstract class AbstractResourceEventSource<R, P extends HasMetadata>
    extends AbstractEventSource
    implements ResourceEventSource<R, P> {
  private final Class<R> resourceClass;

  private Predicate<R> onAddFilter;
  private BiPredicate<R, R> onUpdateFilter;
  private BiPredicate<R, Boolean> onDeleteFilter;
  private Predicate<R> genericFilter;

  protected AbstractResourceEventSource(Class<R> resourceClass) {
    this.resourceClass = resourceClass;
  }

  @Override
  public Class<R> resourceType() {
    return resourceClass;
  }

  public void setOnAddFilter(Predicate<R> onAddFilter) {
    this.onAddFilter = onAddFilter;
  }

  public void setOnUpdateFilter(
      BiPredicate<R, R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
  }

  public void setOnDeleteFilter(
      BiPredicate<R, Boolean> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
  }

  public void setGenericFilter(Predicate<R> genericFilter) {
    this.genericFilter = genericFilter;
  }

  protected boolean hasGenericFilter() {
    return genericFilter != null;
  }

  protected boolean hasOnAddFilter() {
    return onAddFilter != null;
  }

  protected boolean hasOnUpdateFilter() {
    return onUpdateFilter != null;
  }

  protected boolean hasOnDeleteFilter() {
    return onDeleteFilter != null;
  }

  protected boolean eventAcceptedByFilters(ResourceAction action, R resource, R oldResource) {
    return eventAcceptedByFilters(action, resource, oldResource, null);
  }

  protected boolean eventAcceptedByDeleteFilters(R resource, boolean deletedFinalStateUnknown) {
    return eventAcceptedByFilters(ResourceAction.DELETED, resource, null, deletedFinalStateUnknown);
  }

  private boolean eventAcceptedByFilters(ResourceAction action, R resource, R oldResource,
      Boolean deletedFinalStateUnknown) {
    // delete event is filtered for generic filter only.
    if (!acceptedByGenericFilter(resource)) {
      return false;
    }

    switch (action) {
      case ADDED:
        return acceptedByOnAddFilter(resource);
      case UPDATED:
        return acceptedByOnUpdateFilter(resource, oldResource);
      case DELETED:
        return deletedFinalStateUnknown == null
            || acceptedByOnDeleteFilter(resource, deletedFinalStateUnknown);
    }
    return true;
  }

  protected boolean acceptedByOnDeleteFilter(R resource, boolean deletedFinalStateUnknown) {
    return onDeleteFilter == null || onDeleteFilter.test(resource, deletedFinalStateUnknown);
  }

  protected boolean acceptedByOnUpdateFilter(R resource, R oldResource) {
    return onUpdateFilter == null || onUpdateFilter.test(resource, oldResource);
  }

  protected boolean acceptedByOnAddFilter(R resource) {
    return onAddFilter == null || onAddFilter.test(resource);
  }

  protected boolean acceptedByGenericFilter(R resource) {
    return genericFilter == null || genericFilter.test(resource);
  }
}
