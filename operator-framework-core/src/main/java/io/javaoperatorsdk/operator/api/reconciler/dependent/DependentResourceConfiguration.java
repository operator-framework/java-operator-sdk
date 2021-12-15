package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;

public @interface DependentResourceConfiguration {
  boolean CREATABLE_DEFAULT = true;
  boolean UPDATABLE_DEFAULT = false;
  boolean OWNED_DEFAULT = true;
  boolean SKIP_UPDATE_DEFAULT = true;

  boolean creatable() default CREATABLE_DEFAULT;

  boolean updatable() default UPDATABLE_DEFAULT;

  boolean owned() default OWNED_DEFAULT;

  Class<? extends HasMetadata> resourceType();

  Class<? extends Builder> builder() default DEFAULT_BUILDER.class;

  Class<? extends Updater> updater() default DEFAULT_UPDATER.class;

  Class<? extends Fetcher> fetcher() default DEFAULT_FETCHER.class;

  Class<? extends PrimaryResourcesRetriever> associatedPrimariesRetriever() default DEFAULT_PRIMARIES_RETRIEVER.class;

  Class<? extends AssociatedSecondaryIdentifier> associatedSecondaryIdentifier() default DEFAULT_SECONDARY_IDENTIFIER.class;

  boolean skipUpdateIfUnchanged() default SKIP_UPDATE_DEFAULT;

  /**
   * Specified which namespaces this Controller monitors for custom resources events. If no
   * namespace is specified then the controller will monitor all namespaces by default.
   *
   * @return the list of namespaces this controller monitors
   */
  String[] namespaces() default {};

  /**
   * Optional label selector used to identify the set of custom resources the controller will acc
   * upon. The label selector can be made of multiple comma separated requirements that acts as a
   * logical AND operator.
   *
   * @return the label selector
   */
  String labelSelector() default EMPTY_STRING;


  /**
   * Optional list of classes providing custom {@link ResourceEventFilter}.
   *
   * @return the list of event filters.
   */
  @SuppressWarnings("rawtypes")
  Class<ResourceEventFilter>[] eventFilters() default {};


  final class DEFAULT_BUILDER implements Builder<HasMetadata, HasMetadata> {

    @Override
    public HasMetadata buildFor(HasMetadata primary) {
      return null;
    }
  }

  final class DEFAULT_UPDATER implements Updater<HasMetadata, HasMetadata> {

    @Override
    public HasMetadata update(HasMetadata fetched, HasMetadata primary) {
      return null;
    }
  }

  final class DEFAULT_FETCHER implements Fetcher<HasMetadata> {

    @Override
    public HasMetadata fetchFor(HasMetadata owner, ResourceCache<HasMetadata> cache) {
      return null;
    }
  }

  final class DEFAULT_PRIMARIES_RETRIEVER
      implements PrimaryResourcesRetriever<HasMetadata, HasMetadata> {

    @Override
    public Set<ResourceID> associatedPrimaryResources(HasMetadata dependentResource,
        EventSourceRegistry<HasMetadata> registry) {
      return null;
    }
  }

  final class DEFAULT_SECONDARY_IDENTIFIER implements AssociatedSecondaryIdentifier<HasMetadata> {

    @Override
    public ResourceID associatedSecondaryID(HasMetadata primary,
        EventSourceRegistry<HasMetadata> registry) {
      return null;
    }
  }
}
