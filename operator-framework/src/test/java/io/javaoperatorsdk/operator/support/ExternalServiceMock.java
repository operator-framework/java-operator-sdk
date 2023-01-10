package io.javaoperatorsdk.operator.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ExternalServiceMock {

  private static final ExternalServiceMock serviceMock = new ExternalServiceMock();

  private final Map<String, ExternalResource> resourceMap = new ConcurrentHashMap<>();

  public ExternalResource create(ExternalResource externalResource) {
    if (externalResource.getId() == null) {
      throw new IllegalArgumentException("id of the resource is null");
    }
    resourceMap.put(externalResource.getId(), externalResource);
    return externalResource;
  }

  public Optional<ExternalResource> read(String id) {
    return Optional.ofNullable(resourceMap.get(id));
  }

  public ExternalResource update(ExternalResource externalResource) {
    return resourceMap.put(externalResource.getId(), externalResource);
  }

  public Optional<ExternalResource> delete(String id) {
    return Optional.ofNullable(resourceMap.remove(id));
  }

  public List<ExternalResource> listResources() {
    return new ArrayList<>(resourceMap.values());
  }

  public static ExternalServiceMock getInstance() {
    return serviceMock;
  }
}
