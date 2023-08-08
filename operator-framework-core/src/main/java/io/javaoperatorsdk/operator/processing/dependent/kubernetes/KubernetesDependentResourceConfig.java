package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;

public class KubernetesDependentResourceConfig<R> {

  public static final boolean DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA = true;

  private Set<String> namespaces;
  private String labelSelector;
  private boolean namespacesWereConfigured;
  private boolean createResourceOnlyIfNotExistingWithSSA;
  private ResourceDiscriminator<R, ?> resourceDiscriminator;

  private final OnAddFilter<R> onAddFilter;
  private final OnUpdateFilter<R> onUpdateFilter;
  private final OnDeleteFilter<R> onDeleteFilter;
  private final GenericFilter<R> genericFilter;

  public KubernetesDependentResourceConfig() {
    this(Constants.SAME_AS_CONTROLLER_NAMESPACES_SET, NO_VALUE_SET, true,false,
        null, null,
        null, null, null);
  }

  public KubernetesDependentResourceConfig(Set<String> namespaces,
      String labelSelector,
      boolean configuredNS,
      boolean createResourceOnlyIfNotExistingWithSSA,
      ResourceDiscriminator<R, ?> resourceDiscriminator,
      OnAddFilter<R> onAddFilter,
      OnUpdateFilter<R> onUpdateFilter,
      OnDeleteFilter<R> onDeleteFilter, GenericFilter<R> genericFilter) {
    this.namespaces = namespaces;
    this.labelSelector = labelSelector;
    this.namespacesWereConfigured = configuredNS;
    this.createResourceOnlyIfNotExistingWithSSA = createResourceOnlyIfNotExistingWithSSA;
    this.onAddFilter = onAddFilter;
    this.onUpdateFilter = onUpdateFilter;
    this.onDeleteFilter = onDeleteFilter;
    this.genericFilter = genericFilter;
    this.resourceDiscriminator = resourceDiscriminator;
  }

  // use builder instead
  @Deprecated(forRemoval = true)
  public KubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector) {
    this(namespaces, labelSelector, true, DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA,
        null, null, null,
        null, null);
  }

  // use builder instead
  @Deprecated(forRemoval = true)
  public KubernetesDependentResourceConfig<R> setLabelSelector(String labelSelector) {
    this.labelSelector = labelSelector;
    return this;
  }

  public Set<String> namespaces() {
    return namespaces;
  }

  public String labelSelector() {
    return labelSelector;
  }

  public boolean wereNamespacesConfigured() {
    return namespacesWereConfigured;
  }

  @SuppressWarnings("rawtypes")
  public OnAddFilter onAddFilter() {
    return onAddFilter;
  }

  public boolean createResourceOnlyIfNotExistingWithSSA() {
    return createResourceOnlyIfNotExistingWithSSA;
  }

  public OnUpdateFilter<R> onUpdateFilter() {
    return onUpdateFilter;
  }

  public OnDeleteFilter<R> onDeleteFilter() {
    return onDeleteFilter;
  }

  public GenericFilter<R> genericFilter() {
    return genericFilter;
  }

  @SuppressWarnings("rawtypes")
  public ResourceDiscriminator getResourceDiscriminator() {
    return resourceDiscriminator;
  }

  @SuppressWarnings("unused")
  protected void setNamespaces(Set<String> namespaces) {
    if (!wereNamespacesConfigured() && namespaces != null && !namespaces.isEmpty()) {
      this.namespaces = namespaces;
    }
  }
}
