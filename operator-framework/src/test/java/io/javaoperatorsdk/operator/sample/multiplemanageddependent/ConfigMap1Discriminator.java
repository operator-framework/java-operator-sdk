package io.javaoperatorsdk.operator.sample.multiplemanageddependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.sample.multiplemanageddependent.MultipleManagedDependentResourceConfigMap1.NAME_SUFFIX;

public class ConfigMap1Discriminator
    implements ResourceDiscriminator<ConfigMap, MultipleManagedDependentResourceCustomResource> {
  @Override
  public Optional<ConfigMap> distinguish(Class<ConfigMap> resource,
      MultipleManagedDependentResourceCustomResource primary,
      Context<MultipleManagedDependentResourceCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleManagedDependentResourceCustomResource> ies =
        (InformerEventSource<ConfigMap, MultipleManagedDependentResourceCustomResource>) context
            .eventSourceRetriever().getResourceEventSourceFor(ConfigMap.class);

    return ies.get(new ResourceID(primary.getMetadata().getName() + NAME_SUFFIX,
        primary.getMetadata().getNamespace()));
  }
}
