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
package io.javaoperatorsdk.operator.sample.probes;

import java.io.IOException;

import io.javaoperatorsdk.operator.Operator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import static io.javaoperatorsdk.operator.sample.probes.StartupHandler.sendMessage;

public class LivenessHandler implements HttpHandler {

  private final Operator operator;

  public LivenessHandler(Operator operator) {
    this.operator = operator;
  }

  // custom logic can be added here based on the health of event sources
  @Override
  public void handle(HttpExchange httpExchange) throws IOException {
    if (operator.getRuntimeInfo().allEventSourcesAreHealthy()) {
      sendMessage(httpExchange, 200, "healthy");
    } else {
      sendMessage(httpExchange, 400, "an event source is not healthy");
    }
  }
}
