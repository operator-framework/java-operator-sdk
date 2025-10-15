package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class DependentEvent extends Event {

  private final ResourceID dependent;
  private final String dependentResourceVersion;
  private final String kind;
  private final boolean fromOperator;

  public DependentEvent(
      ResourceID targetCustomResource,
      HasMetadata dependent,
      boolean fromOperator) {
    super(targetCustomResource);
    this.dependent = ResourceID.fromResource(dependent);
    this.dependentResourceVersion = dependent.getMetadata().getResourceVersion();
    this.kind = dependent.getKind();
    this.fromOperator = fromOperator;
  }

  public ResourceID getDependent() {
    return dependent;
  }

  public String getDependentResourceVersion() {
    return dependentResourceVersion;
  }
  
  public String getKind() {
    return kind;
  }

  public boolean isFromOperator() {
    return fromOperator;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    DependentEvent that = (DependentEvent) o;
    return Objects.equals(dependent, that.dependent)
        && Objects.equals(dependentResourceVersion, that.dependentResourceVersion)
        && Objects.equals(kind, that.kind)
        && fromOperator == that.fromOperator;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), dependent, dependentResourceVersion, kind, fromOperator);
  }
}
