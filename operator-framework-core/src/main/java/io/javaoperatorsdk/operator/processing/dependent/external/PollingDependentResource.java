package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

public class PollingDependentResource<R, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  PollingEventSource<R, P> pollingEventSource;


  @Override
  public Optional<R> getResource(P primaryResource) {
    return pollingEventSource.getAssociated(primaryResource);
  }


}
