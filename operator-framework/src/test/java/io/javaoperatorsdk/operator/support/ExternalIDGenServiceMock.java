/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
