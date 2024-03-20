package io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype;

import static io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype.MultipleManagedDependentResourceConfigMap2.NAME_SUFFIX;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class ConfigMap2Discriminator
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
