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

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

/**
 * The contract consumed by the event sources. Implementations must extend {@link
 * AbstractInformerPool} — that is the type the configuration API accepts — which additionally
 * handles informer creation, startup and the {@link ConfigurationService} injection.
 */
@Experimental(
    "This is experimental only in the sense that the API could be improved in a"
        + " non-backwards-compatible way. The feature we provide otherwise is prod ready.")
public interface InformerPool {

  /**
   * The informer backing the event source identified by {@code controllerName} and {@code name}: a
   * sharing pool returns the existing informer for an equal {@link InformerClassifier} if there is
   * one and creates it otherwise, a non-sharing pool always creates a dedicated one. A newly
   * created informer is created from the classifier's {@link InformerClassifier#client()}, which is
   * part of the classifier's identity precisely so that a shared informer is only ever handed to
   * event sources watching through that same client.
   *
   * <p>The returned informer is <strong>not</strong> started, callers are expected to call {@link
   * #start(SharedIndexInformer, InformerClassifier)} afterwards. When joining an already running
   * shared informer it may however be started and hold a populated cache already; handlers
   * registered on it still receive the cache contents, so callers must not replay those themselves.
   *
   * <p>This registers the caller as a user of the informer and must therefore be paired with
   * exactly one {@link #releaseInformer(String, String, InformerClassifier)} for the same
   * controller name, event source name and classifier. Requesting an informer twice for the same
   * combination without releasing it in between is a programming error: a sharing pool would count
   * the caller twice and consequently never stop the informer, which is why {@link
   * NonSharingInformerPool} rejects it outright.
   */
  <R extends HasMetadata> SharedIndexInformer<R> getInformer(
      String controllerName, String name, InformerClassifier<R> classifier);

  /**
   * Starts the informer (if not already started) and blocks until its cache has synced, or the
   * configured {@link ConfigurationService#cacheSyncTimeout()} elapses. Callers are expected to
   * invoke this after {@link #getInformer(String, String, InformerClassifier)} returns; the pool
   * itself only registers/reference-counts the informer and does not block on cache sync
   * internally.
   */
  <R extends HasMetadata> void start(
      SharedIndexInformer<R> informer, InformerClassifier<R> classifier);

  /**
   * Signals that the identified user (controller + event source name) no longer needs the informer
   * for the given classifier. A sharing pool only stops the informer once its last user has
   * released it, a non-sharing pool stops it right away.
   *
   * <p>The informer is returned in either case, even when it is left running for the remaining
   * users, since the caller still has to remove its own event handler from it. Callers must not
   * assume the returned informer is stopped, and must not stop it themselves.
   *
   * @return the released informer, or empty if the pool holds none for this user and classifier
   */
  <R extends HasMetadata> Optional<SharedIndexInformer<R>> releaseInformer(
      String controllerName, String name, InformerClassifier<R> classifier);

  /**
   * Binds this pool to the {@link ConfigurationService} it belongs to. Called by the framework when
   * the pool is resolved from that configuration service, before the pool is used; users are not
   * expected to call it themselves.
   *
   * <p>The pool needs the configuration service to create and start informers: the {@link
   * ConfigurationService#cacheSyncTimeout()} to wait for, whether to {@link
   * ConfigurationService#stopOnInformerErrorDuringStartup()}, and the {@link
   * ConfigurationService#getInformerStoppedHandler()} to hook up.
   *
   * <p>Injecting it here, rather than requiring it as a constructor argument, is what keeps
   * creating a pool a plain {@code new NonSharingInformerPool()} for users configuring one through
   * {@link io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider#withInformerPool}.
   * A pool instance therefore belongs to exactly one configuration service.
   */
  void setConfigurationService(ConfigurationService configurationService);
}
