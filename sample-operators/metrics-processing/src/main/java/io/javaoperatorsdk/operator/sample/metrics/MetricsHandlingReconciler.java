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
package io.javaoperatorsdk.operator.sample.metrics;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.metrics.customresource.WebPage;

@ControllerConfiguration
public class MetricsHandlingReconciler implements Reconciler<WebPage> {

  public static final String INDEX_HTML = "index.html";

  private static final Logger log = LoggerFactory.getLogger(MetricsHandlingReconciler.class);

  public MetricsHandlingReconciler() {}

  @Override
  public List<EventSource<?, WebPage>> prepareEventSources(EventSourceContext<WebPage> context) {
    return List.of();
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {

    return UpdateControl.noUpdate();
  }
}
