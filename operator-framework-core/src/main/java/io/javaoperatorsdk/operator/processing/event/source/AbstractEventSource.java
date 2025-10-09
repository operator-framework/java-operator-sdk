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
package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public abstract class AbstractEventSource<R, P extends HasMetadata> implements EventSource<R, P> {

  private final Class<R> resourceClass;

  protected OnAddFilter<? super R> onAddFilter;
  protected OnUpdateFilter<? super R> onUpdateFilter;
  protected OnDeleteFilter<? super R> onDeleteFilter;
  protected GenericFilter<? super R> genericFilter;

  private EventHandler handler;
  private volatile boolean running = false;
  private EventSourceStartPriority eventSourceStartPriority = EventSourceStartPriority.DEFAULT;
  private final String name;

  protected AbstractEventSource(Class<R> resourceClass) {
    this(resourceClass, null);
  }

  protected AbstractEventSource(Class<R> resourceClass, String name) {
    this.name = name == null ? EventSource.super.name() : name;
    this.resourceClass = resourceClass;
  }

  @Override
  public String name() {
    return name;
  }

  protected EventHandler getEventHandler() {
    return handler;
  }

  @Override
  public void setEventHandler(EventHandler handler) {
    this.handler = handler;
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public void start() throws OperatorException {
    running = true;
  }

  @Override
  public void stop() throws OperatorException {
    running = false;
  }

  @Override
  public EventSourceStartPriority priority() {
    return eventSourceStartPriority;
  }

  public AbstractEventSource setEventSourcePriority(
      EventSourceStartPriority eventSourceStartPriority) {
    this.eventSourceStartPriority = eventSourceStartPriority;
    return this;
  }

  @Override
  public Class<R> resourceType() {
    return resourceClass;
  }

  public void setOnAddFilter(OnAddFilter<? super R> onAddFilter) {
    this.onAddFilter = onAddFilter;
  }

  public void setOnUpdateFilter(OnUpdateFilter<? super R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
  }

  public void setOnDeleteFilter(OnDeleteFilter<? super R> onDeleteFilter) {
    this.onDeleteFilter = onDeleteFilter;
  }

  public void setGenericFilter(GenericFilter<? super R> genericFilter) {
    this.genericFilter = genericFilter;
  }
}
