package io.javaoperatorsdk.operator.sample.multipledependentsametypemultiinformer;

import static io.javaoperatorsdk.operator.sample.multiplemanageddependentsametype.MultipleManagedDependentResourceConfigMap1.NAME_SUFFIX;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;

public class ConfigMap1MultiInformerDiscriminator
    implements
    ResourceDiscriminator<ConfigMap, MultipleManagedDependentResourceMultiInformerCustomResource> {
  @Override
  public Optional<ConfigMap> distinguish(Class<ConfigMap> resource,
      MultipleManagedDependentResourceMultiInformerCustomResource primary,
      Context<MultipleManagedDependentResourceMultiInformerCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleManagedDependentResourceMultiInformerCustomResource> ies =
        (InformerEventSource<ConfigMap, MultipleManagedDependentResourceMultiInformerCustomResource>) context
            .eventSourceRetriever().getResourceEventSourceFor(ConfigMap.class,
                MultipleManagedDependentResourceMultiInformerReconciler.CONFIG_MAP_1_DR);

    return ies.get(new ResourceID(primary.getMetadata().getName() + NAME_SUFFIX,
        primary.getMetadata().getNamespace()));
  }
}
