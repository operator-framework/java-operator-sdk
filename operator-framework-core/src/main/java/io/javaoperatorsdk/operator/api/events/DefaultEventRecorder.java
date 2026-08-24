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
package io.javaoperatorsdk.operator.api.events;

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
 * recorded for it rather than to a duplicate, see {@link DefaultEventSink}.
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

  private final String reportingController;
  private final String reportingInstance;
  private final String clusterScopedEventNamespace;
  private final EventSink sink;

  public DefaultEventRecorder(
      String reportingController, String reportingInstance, EventSink sink) {
    this(reportingController, reportingInstance, CLUSTER_SCOPED_EVENT_NAMESPACE, sink);
  }

  public DefaultEventRecorder(
      String reportingController,
      String reportingInstance,
      String clusterScopedEventNamespace,
      EventSink sink) {
    this.reportingController = reportingController;
    this.reportingInstance = reportingInstance;
    this.clusterScopedEventNamespace = clusterScopedEventNamespace;
    this.sink = sink;
  }

  /**
   * The instance name to report events under, when it is not otherwise configured. Uses the host
   * name, which for an operator running in a pod is the pod name.
   */
  public static String defaultReportingInstance() {
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

  @Override
  public void record(HasMetadata regarding, EventRecord event) {
    Objects.requireNonNull(regarding, "the object the event is about must not be null");
    Objects.requireNonNull(event, "event must not be null");
    try {
      sink.emit(toEvent(regarding, event));
    } catch (Exception e) {
      // recording an event must never break the caller: a controller that fails to reconcile
      // because it could not write an event is strictly worse than one that records nothing
      log.warn(
          "Could not record {} event with reason {} for resource {} in namespace {}",
          event.type(),
          event.reason(),
          regarding.getMetadata().getName(),
          regarding.getMetadata().getNamespace(),
          e);
    }
  }

  @Override
  public ResourceEventRecorder forResource(HasMetadata regarding) {
    Objects.requireNonNull(regarding, "the object events will be about must not be null");
    return new BoundEventRecorder(this, regarding);
  }

  protected Event toEvent(HasMetadata regarding, EventRecord record) {
    var now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    var involvedObject = objectReferenceFor(regarding);
    var builder =
        new EventBuilder()
            .withNewMetadata()
            .withName(eventName(regarding, record))
            .withNamespace(eventNamespace(regarding))
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
            .withReportingComponent(record.reportingComponent().orElse(reportingController))
            .withReportingInstance(reportingInstance)
            // the deprecated source is still what kubectl renders in the "From" column
            .withNewSource()
            .withComponent(record.reportingComponent().orElse(reportingController))
            .endSource();
    record.action().ifPresent(builder::withAction);
    return builder.build();
  }

  private String eventNamespace(HasMetadata regarding) {
    var namespace = regarding.getMetadata().getNamespace();
    return namespace == null ? clusterScopedEventNamespace : namespace;
  }

  /**
   * Names events {@code <object name>.<hash>}, following the convention of the Go client, hashing
   * everything that makes two events the same event: the object, the type, the reason, the
   * reporting component and, unless the record sets a {@link EventRecord#key()}, the message. The
   * name is therefore stable across occurrences, which is what lets the sink recognise a repeat,
   * and stays so across operator restarts and between replicas, unlike a name remembered in memory.
   *
   * <p>The object is identified by its uid, with the kind as a fallback for objects that do not
   * have one yet, such as a dependent resource that has only been built so far.
   */
  private String eventName(HasMetadata regarding, EventRecord record) {
    var metadata = regarding.getMetadata();
    var identity =
        String.join(
            String.valueOf(IDENTITY_SEPARATOR),
            requireNonNullElse(regarding.getKind(), ""),
            requireNonNullElse(metadata.getUid(), ""),
            record.type().value(),
            record.reason(),
            record.reportingComponent().orElse(reportingController),
            record.key().orElseGet(() -> requireNonNullElse(record.message(), "")));

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

  private record BoundEventRecorder(EventRecorder delegate, HasMetadata regarding)
      implements ResourceEventRecorder {

    @Override
    public void normal(String reason, String message) {
      record(EventRecord.normal(reason, message));
    }

    @Override
    public void warn(String reason, String message) {
      record(EventRecord.warning(reason, message));
    }

    @Override
    public void record(EventRecord event) {
      delegate.record(regarding, event);
    }
  }
}
