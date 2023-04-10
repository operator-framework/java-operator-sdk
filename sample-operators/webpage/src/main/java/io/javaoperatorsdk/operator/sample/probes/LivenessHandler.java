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
