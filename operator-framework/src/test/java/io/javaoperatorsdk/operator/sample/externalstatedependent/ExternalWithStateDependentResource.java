package io.javaoperatorsdk.operator.sample.externalstatedependent;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;

public class ExternalWithStateDependentResource extends
    PerResourcePollingDependentResource<ExternalResource, ExternalStateDependentCustomResource> {

  ExternalIDGenServiceMock externalIDGenServiceMock = new ExternalIDGenServiceMock();

  public ExternalWithStateDependentResource() {
    super(ExternalResource.class, 300);
  }

  @Override
  public Set<ExternalResource> fetchResources(
      ExternalStateDependentCustomResource primaryResource) {
    return null;
  }


}
