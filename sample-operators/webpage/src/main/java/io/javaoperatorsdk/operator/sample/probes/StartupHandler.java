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
