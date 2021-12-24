package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;

public class InformerConfiguration<R extends HasMetadata, P extends HasMetadata>
    extends DefaultResourceConfiguration<R> {

  private final PrimaryResourcesRetriever<R> secondaryToPrimaryResourcesIdSet;
  private final AssociatedSecondaryResourceIdentifier<P> associatedWith;
  private final boolean skipUpdateEventPropagationIfNoChange;

  private InformerConfiguration(ConfigurationService service, String labelSelector,
      Class<R> resourceClass,
      PrimaryResourcesRetriever<R> secondaryToPrimaryResourcesIdSet,
      AssociatedSecondaryResourceIdentifier<P> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange, Set<String> namespaces) {
    super(labelSelector, resourceClass, namespaces);
    setConfigurationService(service);
    this.secondaryToPrimaryResourcesIdSet =
        Objects.requireNonNullElse(secondaryToPrimaryResourcesIdSet, Mappers.fromOwnerReference());
    this.associatedWith =
        Objects.requireNonNullElseGet(associatedWith, () -> ResourceID::fromResource);
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;
  }

  public PrimaryResourcesRetriever<R> getPrimaryResourcesRetriever() {
    return secondaryToPrimaryResourcesIdSet;
  }

  public AssociatedSecondaryResourceIdentifier<P> getAssociatedResourceIdentifier() {
    return associatedWith;
  }

  public boolean isSkipUpdateEventPropagationIfNoChange() {
    return skipUpdateEventPropagationIfNoChange;
  }

  public static class InformerConfigurationBuilder<R extends HasMetadata, P extends HasMetadata> {

    private PrimaryResourcesRetriever<R> secondaryToPrimaryResourcesIdSet;
    private AssociatedSecondaryResourceIdentifier<P> associatedWith;
    private boolean skipUpdateEventPropagationIfNoChange = true;
    private Set<String> namespaces;
    private String labelSelector;
    private final Class<R> resourceClass;
    private final ConfigurationService configurationService;

    private InformerConfigurationBuilder(Class<R> resourceClass,
        ConfigurationService configurationService) {
      this.resourceClass = resourceClass;
      this.configurationService = configurationService;
    }

    public InformerConfigurationBuilder<R, P> withPrimaryResourcesRetriever(
        PrimaryResourcesRetriever<R> primaryResourcesRetriever) {
      this.secondaryToPrimaryResourcesIdSet = primaryResourcesRetriever;
      return this;
    }

    public InformerConfigurationBuilder<R, P> withAssociatedSecondaryResourceIdentifier(
        AssociatedSecondaryResourceIdentifier<P> associatedWith) {
      this.associatedWith = associatedWith;
      return this;
    }

    public InformerConfigurationBuilder<R, P> withoutSkippingEventPropagationIfUnchanged() {
      this.skipUpdateEventPropagationIfNoChange = false;
      return this;
    }

    public InformerConfigurationBuilder<R, P> skippingEventPropagationIfUnchanged(
        boolean skipIfUnchanged) {
      this.skipUpdateEventPropagationIfNoChange = skipIfUnchanged;
      return this;
    }

    public InformerConfigurationBuilder<R, P> withNamespaces(String... namespaces) {
      this.namespaces = namespaces != null ? Set.of(namespaces) : Collections.emptySet();
      return this;
    }

    public InformerConfigurationBuilder<R, P> withNamespaces(Set<String> namespaces) {
      this.namespaces = namespaces != null ? namespaces : Collections.emptySet();
      return this;
    }


    public InformerConfigurationBuilder<R, P> withLabelSelector(String labelSelector) {
      this.labelSelector = labelSelector;
      return this;
    }

    public InformerConfiguration<R, P> build() {
      return new InformerConfiguration<>(configurationService, labelSelector, resourceClass,
          secondaryToPrimaryResourcesIdSet, associatedWith, skipUpdateEventPropagationIfNoChange,
          namespaces);
    }
  }

  public static <R extends HasMetadata, P extends HasMetadata> InformerConfigurationBuilder<R, P> from(
      EventSourceContext<P> context, Class<R> resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass, context.getConfigurationService());
  }

  public static InformerConfigurationBuilder from(ConfigurationService configurationService,
      Class resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass, configurationService);
  }

  public static <R extends HasMetadata, P extends HasMetadata> InformerConfigurationBuilder<R, P> from(
      InformerConfiguration<R, P> configuration) {
    return new InformerConfigurationBuilder<R, P>(configuration.getResourceClass(),
        configuration.getConfigurationService())
            .withNamespaces(configuration.getNamespaces())
            .withLabelSelector(configuration.getLabelSelector())
            .skippingEventPropagationIfUnchanged(
                configuration.isSkipUpdateEventPropagationIfNoChange())
            .withAssociatedSecondaryResourceIdentifier(
                configuration.getAssociatedResourceIdentifier())
            .withPrimaryResourcesRetriever(configuration.getPrimaryResourcesRetriever());
  }
}
