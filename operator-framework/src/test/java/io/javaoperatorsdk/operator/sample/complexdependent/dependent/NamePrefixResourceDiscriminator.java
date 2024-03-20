package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentCustomResource;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class NamePrefixResourceDiscriminator<R extends HasMetadata>
    implements ResourceDiscriminator<R, ComplexDependentCustomResource> {

  private final String prefix;

  protected NamePrefixResourceDiscriminator(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public Optional<R> distinguish(Class<R> resource, ComplexDependentCustomResource primary,
      Context<ComplexDependentCustomResource> context) {
    var resources = context.getSecondaryResources(resource);
    var filtered = resources.stream().filter(r -> r.getMetadata().getName().startsWith(prefix))
        .collect(Collectors.toList());
    if (filtered.size() > 1) {
      throw new IllegalStateException("More resources than expected for" + primary);
    }
    return filtered.isEmpty() ? Optional.empty() : Optional.of(filtered.get(0));
  }
}
