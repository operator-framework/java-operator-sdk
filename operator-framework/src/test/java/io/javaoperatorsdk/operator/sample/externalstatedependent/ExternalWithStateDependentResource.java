package io.javaoperatorsdk.operator.sample.externalstatedependent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.ExplicitIDHandler;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;

import static io.javaoperatorsdk.operator.sample.externalstatedependent.ExternalStateDependentReconciler.ID_KEY;

public class ExternalWithStateDependentResource extends
    PerResourcePollingDependentResource<ExternalResource, ExternalStateDependentCustomResource>
    implements
    ExplicitIDHandler<ExternalResource, ExternalStateDependentCustomResource, ConfigMap> {

  ExternalIDGenServiceMock externalService = new ExternalIDGenServiceMock();

  public ExternalWithStateDependentResource() {
    super(ExternalResource.class, 300);
  }

  @Override
  public Set<ExternalResource> fetchResources(
      ExternalStateDependentCustomResource primaryResource) {
    var configMap = (ConfigMap) getExternalStateEventSource().getSecondaryResource(primaryResource)
        .orElse(null);
    if (configMap == null) {
      return Collections.emptySet();
    }
    var id = configMap.getData().get(ID_KEY);
    var externalResource = externalService.read(id);
    return externalResource.map(er -> Set.of(er)).orElse(Collections.emptySet());
  }

  @Override
  protected ExternalResource desired(ExternalStateDependentCustomResource primary, Context<ExternalStateDependentCustomResource> context) {
    return new ExternalResource(primary.getSpec().getData());
  }

  @Override
  public Class<ConfigMap> stateResourceClass() {
    return ConfigMap.class;
  }

  @Override
  public ConfigMap stateResource(ExternalStateDependentCustomResource primary,
      ExternalResource resource) {
    ConfigMap configMap = new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of(ID_KEY, resource.getId()))
        .build();
    primary.addOwnerReference(primary);
    return configMap;
  }

  @Override
  public ExternalResource create(ExternalResource desired,
      ExternalStateDependentCustomResource primary,
      Context<ExternalStateDependentCustomResource> context) {
    return externalService.create(desired);
  }

  @Override
  public Matcher.Result<ExternalResource> match(ExternalResource resource, ExternalStateDependentCustomResource primary,
                                                Context<ExternalStateDependentCustomResource> context) {
    return Matcher.Result.nonComputed(resource.getData().equals(primary.getSpec().getData()));
  }
}
