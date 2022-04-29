package io.javaoperatorsdk.operator.api.config.informer;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.DefaultResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@SuppressWarnings("rawtypes")
public interface InformerConfiguration<R extends HasMetadata>
    extends ResourceConfiguration<R> {

  class DefaultInformerConfiguration<R extends HasMetadata> extends
      DefaultResourceConfiguration<R> implements InformerConfiguration<R> {

    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private final boolean inheritControllerNamespaces;

    protected DefaultInformerConfiguration(String labelSelector,
        Class<R> resourceClass,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        Set<String> namespaces, Boolean inheritControllerNamespaces) {
      super(labelSelector, resourceClass, namespaces);
      this.inheritControllerNamespaces = inheritControllerNamespaces;
      this.secondaryToPrimaryMapper =
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReference());
    }

    public boolean isInheritControllerNamespacesOnChange() {
      return inheritControllerNamespaces;
    }

    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

  }

  boolean isInheritControllerNamespacesOnChange();

  SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper();

  @SuppressWarnings("unused")
  class InformerConfigurationBuilder<R extends HasMetadata> {

    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private Set<String> namespaces;
    private String labelSelector;
    private final Class<R> resourceClass;
    private boolean inheritControllerNamespacesOnChange = false;

    private InformerConfigurationBuilder(Class<R> resourceClass) {
      this.resourceClass = resourceClass;
    }

    public InformerConfigurationBuilder<R> withSecondaryToPrimaryMapper(
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
      return this;
    }

    public InformerConfigurationBuilder<R> withNamespaces(String... namespaces) {
      this.namespaces = namespaces != null ? Set.of(namespaces) : Collections.emptySet();
      return this;
    }

    public InformerConfigurationBuilder<R> withNamespaces(Set<String> namespaces) {
      this.namespaces = namespaces != null ? namespaces : Collections.emptySet();
      return this;
    }

    public InformerConfigurationBuilder<R> withLabelSelector(String labelSelector) {
      this.labelSelector = labelSelector;
      return this;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> setAndInheritControllerNamespaces(
        Set<String> namespaces) {
      this.namespaces = namespaces;
      this.inheritControllerNamespacesOnChange = true;
      return this;
    }

    public <P extends HasMetadata> InformerConfigurationBuilder<R> setAndInheritControllerNamespaces(
        EventSourceContext<P> context) {
      namespaces = context.getControllerConfiguration().getEffectiveNamespaces();
      this.inheritControllerNamespacesOnChange = true;
      return this;
    }

    public InformerConfiguration<R> build() {
      return new DefaultInformerConfiguration<>(labelSelector, resourceClass,
          secondaryToPrimaryMapper,
          namespaces, inheritControllerNamespacesOnChange);
    }
  }

  static <R extends HasMetadata> InformerConfigurationBuilder<R> from(
      Class<R> resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass);
  }

}
