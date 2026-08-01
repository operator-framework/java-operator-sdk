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
package io.javaoperatorsdk.operator.processing.event.source.informer.pool;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.informer.FieldSelector;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;

/**
 * Identifies the informer that backs an event source: two event sources whose classifiers are equal
 * can be served by one shared informer. It also carries everything needed to create that informer,
 * including the {@link KubernetesClient} to create it from.
 *
 * <p>Note that {@link #equals(Object)} and {@link #hashCode()} deliberately do <strong>not</strong>
 * cover every record component:
 *
 * <ul>
 *   <li>{@link #informerListLimit()} is excluded, so event sources that only disagree on the list
 *       limit still share an informer; the limit of whichever classifier created the informer is
 *       kept (a pool is expected to warn about this, see {@link
 *       #differsOnlyByInformerListLimit(InformerClassifier)}).
 *   <li>Indexers are not part of the classifier at all: they are registered on the informer under a
 *       name qualified with the event source that added them, so those of different event sources
 *       can live side by side on a shared informer without colliding.
 * </ul>
 *
 * <p>The {@link #client()} takes part in equality <strong>by identity</strong>: event sources
 * sharing an informer must be watching through the very same client, since the informer is created
 * from (and keeps using) the client of whichever event source established it. Two separate clients
 * are therefore never assumed to be interchangeable, not even when they connect to the same API
 * server — they may well differ in credentials, impersonation or TLS material, and the pool cannot
 * tell.
 *
 * <p>Note that this is also why nothing security relevant from the client's configuration is part
 * of the classifier: instances end up in log messages and exception messages, so a credential held
 * here would leak into those.
 */
public record InformerClassifier<R extends HasMetadata>(
    KubernetesClient client,
    String labelSelector,
    String shardSelector,
    String namespaceIdentifier,
    Class<R> resourceClass,
    GroupVersionKind groupVersionKind,
    FieldSelector fieldSelector,
    Long informerListLimit,
    ItemStore<R> itemStore) {

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InformerClassifier<?> that)) {
      return false;
    }
    return client == that.client
        && Objects.equals(labelSelector, that.labelSelector)
        && Objects.equals(shardSelector, that.shardSelector)
        && Objects.equals(namespaceIdentifier, that.namespaceIdentifier)
        && Objects.equals(resourceClass, that.resourceClass)
        && Objects.equals(groupVersionKind, that.groupVersionKind)
        && Objects.equals(fieldSelector, that.fieldSelector)
        && Objects.equals(itemStore, that.itemStore);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        System.identityHashCode(client),
        labelSelector,
        shardSelector,
        namespaceIdentifier,
        resourceClass,
        groupVersionKind,
        fieldSelector,
        itemStore);
  }

  /**
   * Hand written instead of using the one generated for the record, so that the API server URL is
   * part of it: classifiers show up in log and exception messages, where the client on its own
   * identifies the instance but not the cluster it connects to. The URL is derived from the {@link
   * #client()} rather than held as a component of its own, since it would be redundant for the
   * identity and could only ever contradict the client.
   */
  @Override
  public String toString() {
    return "InformerClassifier[client="
        + client
        + " ("
        + masterUrl()
        + "), labelSelector="
        + labelSelector
        + ", shardSelector="
        + shardSelector
        + ", namespaceIdentifier="
        + namespaceIdentifier
        + ", resourceClass="
        + (resourceClass != null ? resourceClass.getName() : null)
        + ", groupVersionKind="
        + groupVersionKind
        + ", fieldSelector="
        + fieldSelector
        + ", informerListLimit="
        + informerListLimit
        + ", itemStore="
        + itemStore
        + "]";
  }

  private String masterUrl() {
    if (client == null || client.getConfiguration() == null) {
      return null;
    }
    return client.getConfiguration().getMasterUrl();
  }

  /**
   * Checks whether this classifier and the other are equal in every attribute except for the {@link
   * #informerListLimit()}, which differs between them.
   */
  public boolean differsOnlyByInformerListLimit(InformerClassifier<?> other) {
    return equals(other) && !Objects.equals(informerListLimit, other.informerListLimit);
  }
}
