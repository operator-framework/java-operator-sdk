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

  class DefaultInformerConfiguration<R extends HasMetadata, P extends HasMetadata> extends
      DefaultResourceConfiguration<R> implements InformerConfiguration<R> {

    private final SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;

    protected DefaultInformerConfiguration(String labelSelector,
        Class<R> resourceClass,
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper,
        Set<String> namespaces) {
      super(labelSelector, resourceClass, namespaces);
      this.secondaryToPrimaryMapper =
          Objects.requireNonNullElse(secondaryToPrimaryMapper,
              Mappers.fromOwnerReference());
    }


    public SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper() {
      return secondaryToPrimaryMapper;
    }

  }

  SecondaryToPrimaryMapper<R> getSecondaryToPrimaryMapper();

  @SuppressWarnings("unused")
  class InformerConfigurationBuilder<R extends HasMetadata, P extends HasMetadata> {

    private SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper;
    private Set<String> namespaces;
    private String labelSelector;
    private final Class<R> resourceClass;

    private InformerConfigurationBuilder(Class<R> resourceClass) {
      this.resourceClass = resourceClass;
    }

    public InformerConfigurationBuilder<R, P> withSecondaryToPrimaryMapper(
        SecondaryToPrimaryMapper<R> secondaryToPrimaryMapper) {
      this.secondaryToPrimaryMapper = secondaryToPrimaryMapper;
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

    public InformerConfiguration<R> build() {
      return new DefaultInformerConfiguration<>(labelSelector, resourceClass,
          secondaryToPrimaryMapper,
          namespaces);
    }
  }

  static <R extends HasMetadata, P extends HasMetadata> InformerConfigurationBuilder<R, P> from(
      EventSourceContext<P> context, Class<R> resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static InformerConfigurationBuilder from(Class resourceClass) {
    return new InformerConfigurationBuilder<>(resourceClass);
  }

  static <R extends HasMetadata, P extends HasMetadata> InformerConfigurationBuilder<R, P> from(
      InformerConfiguration<R> configuration) {
    return new InformerConfigurationBuilder<R, P>(configuration.getResourceClass())
        .withNamespaces(configuration.getNamespaces())
        .withLabelSelector(configuration.getLabelSelector())
        .withSecondaryToPrimaryMapper(configuration.getSecondaryToPrimaryMapper());
  }
}
