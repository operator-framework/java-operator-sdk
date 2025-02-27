package io.javaoperatorsdk.operator.support;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExternalIDGenServiceMock {

  private static final ExternalIDGenServiceMock serviceMock = new ExternalIDGenServiceMock();

  private final Map<String, ExternalResource> resourceMap = new ConcurrentHashMap<>();

  public ExternalResource create(ExternalResource externalResource) {
    if (externalResource.getId() != null) {
      throw new IllegalArgumentException("ID provided for external resource");
    }
    String id = UUID.randomUUID().toString();
    var newResource = new ExternalResource(id, externalResource.getData());
    resourceMap.put(id, newResource);
    return newResource;
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

  public static ExternalIDGenServiceMock getInstance() {
    return serviceMock;
  }
}
