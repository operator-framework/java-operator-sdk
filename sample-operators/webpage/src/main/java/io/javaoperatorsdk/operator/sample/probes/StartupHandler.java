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
import java.nio.charset.StandardCharsets;

import io.javaoperatorsdk.operator.Operator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class StartupHandler implements HttpHandler {

  private final Operator operator;

  public StartupHandler(Operator operator) {
    this.operator = operator;
  }

  @Override
  public void handle(HttpExchange httpExchange) throws IOException {
    if (operator.getRuntimeInfo().isStarted()) {
      sendMessage(httpExchange, 200, "started");
    } else {
      sendMessage(httpExchange, 400, "not started yet");
    }
  }

  public static void sendMessage(HttpExchange httpExchange, int code, String message)
      throws IOException {
    try (var outputStream = httpExchange.getResponseBody()) {
      var bytes = message.getBytes(StandardCharsets.UTF_8);
      httpExchange.sendResponseHeaders(code, bytes.length);
      outputStream.write(bytes);
      outputStream.flush();
    }
  }
}
