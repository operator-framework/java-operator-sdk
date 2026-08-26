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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes a Kubernetes event to be recorded. Fields that can be derived from the controller and
 * the object the event is about (such as the reporting controller and instance, or the involved
 * object reference) are filled in by the {@link EventRecorder} and are intentionally absent here.
 *
 * <p>Instances are immutable, create them using {@link #builder()}.
 */
public final class EventRecord {

  private final EventType type;
  private final String reason;
  private final String message;
  private final String key;
  private final String action;
  private final String reportingComponent;
  private final Map<String, String> labels;
  private final Map<String, String> annotations;

  private EventRecord(Builder builder) {
    this.type = builder.type;
    this.reason = builder.reason;
    this.message = builder.message;
    this.key = builder.key;
    this.action = builder.action;
    this.reportingComponent = builder.reportingComponent;
    this.labels = Map.copyOf(builder.labels);
    this.annotations = Map.copyOf(builder.annotations);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Shorthand for a {@link EventType#NORMAL} event. */
  public static EventRecord normal(String reason, String message) {
    return builder().type(EventType.NORMAL).reason(reason).message(message).build();
  }

  /** Shorthand for a {@link EventType#WARNING} event. */
  public static EventRecord warning(String reason, String message) {
    return builder().type(EventType.WARNING).reason(reason).message(message).build();
  }

  public EventType type() {
    return type;
  }

  public String reason() {
    return reason;
  }

  public String message() {
    return message;
  }

  /**
   * Identifies this event among the events about the same object, so that repeated occurrences
   * resolve to the same event rather than to one event each.
   */
  public Optional<String> key() {
    return Optional.ofNullable(key);
  }

  /**
   * The action taken or failed regarding the involved object, if any. Optional, and only meaningful
   * for consumers that read the {@code action} field of the event.
   */
  public Optional<String> action() {
    return Optional.ofNullable(action);
  }

  /**
   * The component of the operator reporting this event. Set per event, since a single controller
   * can report on behalf of several logical components. When absent, the recorder uses the
   * controller name.
   */
  public Optional<String> reportingComponent() {
    return Optional.ofNullable(reportingComponent);
  }

  public Map<String, String> labels() {
    return labels;
  }

  public Map<String, String> annotations() {
    return annotations;
  }

  @Override
  public String toString() {
    return "EventRecord{type=" + type + ", reason=" + reason + ", message=" + message + "}";
  }

  /** Builder for {@link EventRecord}. */
  public static final class Builder {

    private EventType type = EventType.NORMAL;
    private String reason;
    private String message;
    private String key;
    private String action;
    private String reportingComponent;
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> annotations = new HashMap<>();

    private Builder() {}

    public Builder type(EventType type) {
      this.type = Objects.requireNonNull(type, "type must not be null");
      return this;
    }

    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    /** Sets the key identifying this event, see {@link EventRecord#key()}. */
    public Builder key(String key) {
      this.key = key;
      return this;
    }

    public Builder action(String action) {
      this.action = action;
      return this;
    }

    public Builder reportingComponent(String reportingComponent) {
      this.reportingComponent = reportingComponent;
      return this;
    }

    public Builder label(String key, String value) {
      this.labels.put(key, value);
      return this;
    }

    public Builder labels(Map<String, String> labels) {
      this.labels.putAll(labels);
      return this;
    }

    public Builder annotation(String key, String value) {
      this.annotations.put(key, value);
      return this;
    }

    public Builder annotations(Map<String, String> annotations) {
      this.annotations.putAll(annotations);
      return this;
    }

    public EventRecord build() {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must be set on an event record");
      }
      return new EventRecord(this);
    }
  }
}
