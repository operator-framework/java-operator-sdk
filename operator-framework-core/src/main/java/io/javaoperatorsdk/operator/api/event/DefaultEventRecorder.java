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
package io.javaoperatorsdk.operator.api.event;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static java.util.Objects.requireNonNullElse;

/**
 * Default {@link EventRecorder}. Assembles events from an {@link EventRecord} plus the context the
 * controller already knows about (the involved object reference, the reporting controller and
 * instance), then hands them to an {@link EventSink}.
 *
 * <p>Events for cluster scoped objects have to live in some namespace: by default the {@value
 * #CLUSTER_SCOPED_EVENT_NAMESPACE} namespace is used, following the Kubernetes convention, but it
 * can be overridden, see {@link
 * io.javaoperatorsdk.operator.api.config.ConfigurationService#clusterScopedEventNamespace()}.
 *
 * <p>Events are named deterministically, after the object they are about plus a hash of everything
 * that identifies the event, so that recording the same event again resolves to the event already
 * recorded for it. Repeat occurrences are then counted on that event rather than recorded as copies
 * of it, see {@link DefaultEventSink}.
 */
public class DefaultEventRecorder implements EventRecorder {

  private static final Logger log = LoggerFactory.getLogger(DefaultEventRecorder.class);

  public static final String CLUSTER_SCOPED_EVENT_NAMESPACE = "default";

  /**
   * Kubernetes limits object names to 253 characters, as they have to be valid RFC 1123 DNS
   * subdomains.
   */
  private static final int MAX_NAME_LENGTH = 253;

  /** Separates the parts hashed into the event name, so no two sets of parts can collide. */
  private static final char IDENTITY_SEPARATOR = '\0';

  /** Digest used to derive the event name suffix from the identity of the event. */
  private static final String IDENTITY_DIGEST = "SHA-256";

  private static final int IDENTITY_HASH_LENGTH = 32;

  private final EventSink sink;
  private final EventKeyStrategy keyStrategy;
  private final boolean ownerReference;

  public DefaultEventRecorder(EventSink sink) {
    this(sink, EventKeyStrategy.none(), false);
  }

  private DefaultEventRecorder(
      EventSink sink, EventKeyStrategy keyStrategy, boolean ownerReference) {
    this.sink = sink;
    this.keyStrategy = keyStrategy;
    this.ownerReference = ownerReference;
  }

  public static Builder builder(EventSink sink) {
    return new Builder(sink);
  }

  /** Builder for {@link DefaultEventRecorder}. */
  public static final class Builder {

    private final EventSink sink;
    private EventKeyStrategy keyStrategy = EventKeyStrategy.none();
    private boolean ownerReference = false;

    private Builder(EventSink sink) {
      this.sink = Objects.requireNonNull(sink, "sink must not be null");
    }

    /**
     * The strategy deriving the default aggregation key of records that do not set one, see {@link
     * EventKeyStrategy}.
     */
    public Builder keyStrategy(EventKeyStrategy keyStrategy) {
      this.keyStrategy = Objects.requireNonNull(keyStrategy, "keyStrategy must not be null");
      return this;
    }

    /**
     * When set, recorded events carry an {@code ownerReference} to the object they are about. The
     * reference expresses ownership for tooling that reads it; note that the Kubernetes garbage
     * collector ignores events, so it does not cause cascade deletion, events expire through the
     * event TTL either way. Records can override this per event via {@link
     * EventRecord.Builder#ownedByRegarding(boolean)}. The reference is only set when the object
     * already has a uid.
     */
    public Builder ownerReference(boolean ownerReference) {
      this.ownerReference = ownerReference;
      return this;
    }

    public DefaultEventRecorder build() {
      return new DefaultEventRecorder(sink, keyStrategy, ownerReference);
    }
  }

  /**
   * The instance name to report events under, when it is not otherwise configured. Uses the host
   * name, which for an operator running in a pod is the pod name.
   *
   * <p>Resolved once and cached: it cannot change over the life of the process, and looking the
   * host name up can hit the name service, which is not something to do on every recorded event.
   */
  public static String defaultReportingInstance() {
    return DefaultReportingInstance.VALUE;
  }

  private static final class DefaultReportingInstance {
    private static final String VALUE = resolve();

    private static String resolve() {
      var fromEnv = System.getenv("HOSTNAME");
      if (fromEnv != null && !fromEnv.isBlank()) {
        return fromEnv;
      }
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        log.debug("Could not determine host name to report events under", e);
        return "unknown";
      }
    }
  }

  @Override
  public void record(EventRecord event, Context<?> context) {
    Objects.requireNonNull(context, "the context of the reconciliation must not be null");
    Objects.requireNonNull(event, "event must not be null");
    try {
      sink.emit(toEvent(context, event), context);
    } catch (Exception e) {
      // recording an event must never break the caller: a controller that fails to reconcile
      // because it could not write an event is strictly worse than one that records nothing
      log.warn(
          "Could not record {} event with reason {} for resource {} in namespace {}",
          event.type(),
          event.reason(),
          context.getPrimaryResource().getMetadata().getName(),
          context.getPrimaryResource().getMetadata().getNamespace(),
          e);
    }
  }

  @Override
  public ResourceEventRecorder forContext(Context<?> context) {
    Objects.requireNonNull(context, "the context events will be recorded from must not be null");
    return new BoundEventRecorder(this, context);
  }

  protected Event toEvent(Context<?> context, EventRecord record) {
    var controllerName = context.getControllerConfiguration().getName();
    var regarding = context.getPrimaryResource();
    var now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    var involvedObject = objectReferenceFor(regarding);
    var builder =
        new EventBuilder()
            .withNewMetadata()
            .withName(eventName(regarding, record, controllerName))
            .withNamespace(eventNamespace(regarding, context))
            .withLabels(record.labels())
            .withAnnotations(record.annotations())
            .endMetadata()
            .withInvolvedObject(involvedObject)
            .withType(record.type().value())
            .withReason(record.reason())
            .withMessage(record.message())
            .withFirstTimestamp(now)
            .withLastTimestamp(now)
            .withCount(1)
            .withReportingComponent(record.reportingComponent().orElse(controllerName))
            .withReportingInstance(
                context
                    .getControllerConfiguration()
                    .getConfigurationService()
                    .getLeaderElectionConfiguration()
                    .flatMap(LeaderElectionConfiguration::getIdentity)
                    .orElseGet(DefaultEventRecorder::defaultReportingInstance))
            // the deprecated source is still what kubectl renders in the "From" column
            .withNewSource()
            .withComponent(record.reportingComponent().orElse(controllerName))
            .endSource();
    boolean ownedByRegarding = record.ownedByRegarding().orElse(ownerReference);
    if (ownedByRegarding && regarding.getMetadata().getUid() == null) {
      log.debug(
          "Not setting the owner reference on the event about {}: the object has no uid yet",
          regarding.getMetadata().getName());
    }
    if (ownedByRegarding && regarding.getMetadata().getUid() != null) {
      builder
          .editMetadata()
          .addNewOwnerReference()
          .withApiVersion(regarding.getApiVersion())
          .withKind(regarding.getKind())
          .withName(regarding.getMetadata().getName())
          .withUid(regarding.getMetadata().getUid())
          .endOwnerReference()
          .endMetadata();
    }
    record.action().ifPresent(builder::withAction);
    return builder.build();
  }

  private String eventNamespace(HasMetadata regarding, Context<?> context) {
    var namespace = regarding.getMetadata().getNamespace();
    if (namespace != null) {
      return namespace;
    }
    return requireNonNullElse(
        context
            .getControllerConfiguration()
            .getConfigurationService()
            .clusterScopedEventNamespace(),
        CLUSTER_SCOPED_EVENT_NAMESPACE);
  }

  /**
   * Names events {@code <object name>.<hash>}, following the convention of the Go client, hashing
   * everything that makes two events the same event: the object, the type, the reason, the
   * reporting component and, unless the record sets a {@link EventRecord#key()} or the recorder is
   * built with a default {@link EventKeyStrategy}, the message. The name is therefore stable across
   * occurrences, which is what lets the sink recognise a repeat, and stays so across operator
   * restarts and between replicas, unlike a name remembered in memory.
   *
   * <p>The object is identified by its uid, with the kind as a fallback for objects that do not
   * have one yet, such as a dependent resource that has only been built so far.
   */
  private String eventName(HasMetadata regarding, EventRecord record, String reportingController) {
    var metadata = regarding.getMetadata();
    var identity =
        String.join(
            String.valueOf(IDENTITY_SEPARATOR),
            requireNonNullElse(regarding.getKind(), ""),
            requireNonNullElse(metadata.getUid(), ""),
            record.type().value(),
            record.reason(),
            record.reportingComponent().orElse(reportingController),
            record
                .key()
                .or(() -> keyStrategy.keyFor(regarding, record))
                .orElseGet(() -> requireNonNullElse(record.message(), "")));

    var suffix = "." + identityDigest(identity);
    var prefix = metadata.getName();
    var maxPrefixLength = MAX_NAME_LENGTH - suffix.length();
    if (prefix.length() > maxPrefixLength) {
      prefix = prefix.substring(0, maxPrefixLength);
    }
    return prefix + suffix;
  }

  /**
   * Digests the <em>contents</em> of the identity of an event into lowercase hexadecimal, which is
   * valid in an RFC 1123 DNS subdomain. Being a digest of the contents, it is the same in every
   * process and on every machine for the same event, which is what makes the event name stable
   * across restarts and between replicas.
   *
   * <p>A cryptographic digest is used rather than {@link String#hashCode()}: the latter collides on
   * inputs as short as {@code Aa} and {@code BB}, and two colliding events would resolve to the
   * same name, so the sink would take the second one for a repeat of the first and drop it.
   *
   * <p>A {@link MessageDigest} is created per call on purpose, as it is stateful and not thread
   * safe; sharing one across concurrent reconciliations would interleave their digests.
   */
  private static String identityDigest(String identity) {
    try {
      var digest =
          MessageDigest.getInstance(IDENTITY_DIGEST)
              .digest(identity.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, IDENTITY_HASH_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      // every JVM is required to provide SHA-256
      throw new IllegalStateException(IDENTITY_DIGEST + " is not available", e);
    }
  }

  private ObjectReference objectReferenceFor(HasMetadata resource) {
    return new ObjectReferenceBuilder()
        .withApiVersion(resource.getApiVersion())
        .withKind(resource.getKind())
        .withName(resource.getMetadata().getName())
        .withNamespace(resource.getMetadata().getNamespace())
        .withUid(resource.getMetadata().getUid())
        .withResourceVersion(resource.getMetadata().getResourceVersion())
        .build();
  }

  private record BoundEventRecorder(EventRecorder delegate, Context<?> context)
      implements ResourceEventRecorder {

    @Override
    public void normal(String reason, String message) {
      delegate.record(EventRecord.normal(reason, message), context);
    }

    @Override
    public void warn(String reason, String message) {
      delegate.record(EventRecord.warning(reason, message), context);
    }

    @Override
    public void record(EventRecord event) {
      delegate.record(event, context);
    }
  }
}
