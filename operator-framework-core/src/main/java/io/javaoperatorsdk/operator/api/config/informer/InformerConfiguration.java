package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Objects;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.InformerPrimaryToSecondaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

public interface InformerConfiguration<R extends HasMetadata>
    extends ResourceConfiguration<R> {

  class DefaultInformerConfiguration<R extends HasMetadata> extends
      DefaultResourceConfiguration<R> implements InformerConfiguration<R> {

    private final InformerPrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final boolean followControllerNamespaceChanges;

    protected DefaultInformerConfiguration(String labelSelector,
        Class<R> resourceClass,
        InformerPrimaryToSecondaryMapper<?> primaryToSecondaryMapper,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        Set<String> namespaces, boolean followControllerNamespaceChanges) {
      super(labelSelector, resourceClass, namespaces);
      this.followControllerNamespaceChanges = followControllerNamespaceChanges;
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      this.secondaryToPrimaryMapper =
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReference());
    }

    public boolean followControllerNamespaceChanges() {
      return followControllerNamespaceChanges;
    }

    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

    @Override
    public <P extends HasMetadata> InformerPrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper() {
      return (InformerPrimaryToSecondaryMapper<P>) primaryToSecondaryMapper;
    }
  }

  /**
   * Used in case the watched namespaces are changed dynamically, thus when operator is running (See
   * {@link io.javaoperatorsdk.operator.RegisteredController}). If true, changing the target
   * namespaces of a controller would result to change target namespaces for the
   * InformerEventSource.
   */
  boolean followControllerNamespaceChanges();

  SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper();

  <P extends HasMetadata> InformerPrimaryToSecondaryMapper<P> getPrimaryToSecondaryMapper();

  @SuppressWarnings("unused")
  class InformerConfigurationBuilder<R extends HasMetadata> {

    private InformerPrimaryToSecondaryMapper<?> primaryToSecondaryMapper;
    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private Set<String> namespaces;
    private String labelSelector;
    private final Class<R> resourceClass;
    private boolean inheritControllerNamespacesOnChange = false;

    private InformerConfigurationBuilder(Class<R> resourceClass) {
      this.resourceClass = resourceClass;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> withPrimaryToSecondaryMapper(
        InformerPrimaryToSecondaryMapper<P> primaryToSecondaryMapper) {
      this.primaryToSecondaryMapper = primaryToSecondaryMapper;
      return this;
    }

    public InformerConfigurationBuilder<R> withSecondaryToPrimaryMapper(
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
      return this;
    }

    public InformerConfigurationBuilder<R> withNamespaces(String... namespaces) {
      return withNamespaces(
          namespaces != null ? Set.of(namespaces) : DEFAULT_NAMESPACES_SET);
    }

    public InformerConfigurationBuilder<R> withNamespaces(Set<String> namespaces) {
      return withNamespaces(namespaces, false);
    }

    /**
     * Sets the initial set of namespaces to watch (typically extracted from the parent
     * {@link io.javaoperatorsdk.operator.processing.Controller}'s configuration), specifying
     * whether changes made to the parent controller configured namespaces should be tracked or not.
     *
     * @param namespaces the initial set of namespaces to watch
     * @param followChanges {@code true} to follow the changes made to the parent controller
     *        namespaces, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public InformerConfigurationBuilder<R> withNamespaces(Set<String> namespaces,
        boolean followChanges) {
      this.namespaces = namespaces != null ? namespaces : DEFAULT_NAMESPACES_SET;
      this.inheritControllerNamespacesOnChange = true;
      return this;
    }

    /**
     * Configures the informer to watch and track the same namespaces as the parent
     * {@link io.javaoperatorsdk.operator.processing.Controller}, meaning that the informer will be
     * restarted to watch the new namespaces if the parent controller's namespace configuration
     * changes.
     *
     * @param context {@link EventSourceContext} from which the parent
     *        {@link io.javaoperatorsdk.operator.processing.Controller}'s configuration is retrieved
     * @param <P> the primary resource type associated with the parent controller
     * @return the builder instance so that calls can be chained fluently
     */
    public <P extends HasMetadata> InformerConfigurationBuilder<R> withNamespacesInheritedFromController(
        EventSourceContext<P> context) {
      namespaces = context.getControllerConfiguration().getEffectiveNamespaces();
      this.inheritControllerNamespacesOnChange = true;
      return this;
    }

    /**
     * Whether or not the associated informer should track changes made to the parent
     * {@link io.javaoperatorsdk.operator.processing.Controller}'s namespaces configuration.
     *
     * @param followChanges {@code true} to reconfigure the associated informer when the parent
     *        controller's namespaces are reconfigured, {@code false} otherwise
     * @return the builder instance so that calls can be chained fluently
     */
    public InformerConfigurationBuilder<R> followNamespaceChanges(boolean followChanges) {
      this.inheritControllerNamespacesOnChange = followChanges;
      return this;
    }

    public InformerConfigurationBuilder<R> withLabelSelector(String labelSelector) {
      this.labelSelector = labelSelector;
      return this;
    }

    public InformerConfiguration<R> build() {
      return new DefaultInformerConfiguration<>(labelSelector, resourceClass,
          primaryToSecondaryMapper,
          secondaryToPrimaryMapper,
          namespaces, inheritControllerNamespacesOnChange);
    }
  }

  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass);
  }

  /**
   * Creates a configuration builder that inherits namespaces from the controller and follows
   * namespaces changes.
   *
   * @param resourceClass secondary resource class
   * @param eventSourceContext of the initializer
   * @return builder
   * @param <R> secondary resource type
   */
  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass, EventSourceContext<?> eventSourceContext) {
    return new InformerConfigurationBuilder<>(resourceClass)
        .withNamespacesInheritedFromController(eventSourceContext);
  }

  @SuppressWarnings("unchecked")
  @Override
  default Class<R> getResourceClass() {
    return (Class<R>) Utils.getFirstTypeArgumentFromSuperClassOrInterface(getClass(),
        InformerConfiguration.class);
  }
}
