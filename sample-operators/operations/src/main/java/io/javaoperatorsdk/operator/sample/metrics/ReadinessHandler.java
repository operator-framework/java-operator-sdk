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

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import io.javaoperatorsdk.operator.Operator;

import static io.javaoperatorsdk.operator.sample.metrics.StartupHandler.sendMessage;

public class ReadinessHandler extends Handler.Abstract {

  private final Operator operator;

  public ReadinessHandler(Operator operator) {
    this.operator = operator;
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) {
    if (operator.getRuntimeInfo().allEventSourcesAreHealthy()) {
      sendMessage(response, 200, "ready", callback);
    } else {
      sendMessage(response, 400, "not ready: an event source is not healthy", callback);
    }
    return true;
  }
}
