package io.javaoperatorsdk.operator.processing.dependent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public class CRDPresentActivationCondition implements Condition<HasMetadata, HasMetadata> {

  private static final Logger log = LoggerFactory.getLogger(CRDPresentActivationCondition.class);

  @Override
  public boolean isMet(DependentResource<HasMetadata, HasMetadata> dependentResource,
      HasMetadata primary, Context<HasMetadata> context) {

    var resourceClass = dependentResource.resourceType();
    var apiVersion = HasMetadata.getApiVersion(resourceClass);
    var kind = HasMetadata.getKind(resourceClass);
    var gvk = new GroupVersionKind(apiVersion, kind);

    InformerEventSource<CustomResourceDefinition, HasMetadata> crdInformer = null;
    try {
      crdInformer = (InformerEventSource<CustomResourceDefinition, HasMetadata>) context
          .eventSourceRetriever().getResourceEventSourceFor(CustomResourceDefinition.class);
    } catch (IllegalArgumentException e) {
      log.debug("Error when finding event source for CustomResourceDefinitions", e);
    }

    if (crdInformer != null) {
      return crdInformer
          .list(p -> p.getSpec().getNames().getKind().equals(kind)
              && p.getSpec().getGroup().equals(gvk.getGroup()))
          .findAny().isPresent();
    } else {
      // todo poll activation condition
    }
    return false;
  }
}
