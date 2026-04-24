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
package io.javaoperatorsdk.operator.sample.operations;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import io.javaoperatorsdk.operator.Operator;

/**
 * Combined health endpoint that checks whether all event sources (informers, polling sources, etc.)
 * are healthy. Before the operator has fully started the informers will not have synced yet, so
 * this endpoint naturally covers the startup case as well.
 */
public class HealthHandler extends Handler.Abstract {

  private final Operator operator;

  public HealthHandler(Operator operator) {
    this.operator = operator;
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) {
    var runtimeInfo = operator.getRuntimeInfo();
    if (runtimeInfo.isStarted() && runtimeInfo.allEventSourcesAreHealthy()) {
      sendMessage(response, 200, "healthy", callback);
    } else {
      sendMessage(response, 503, "not healthy", callback);
    }
    return true;
  }

  static void sendMessage(Response response, int code, String message, Callback callback) {
    response.setStatus(code);
    response.getHeaders().put("Content-Type", "text/plain; charset=utf-8");
    response.write(true, ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)), callback);
  }
}
