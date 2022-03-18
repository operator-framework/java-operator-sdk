package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;

public class Utils {

  private Utils() {}

  static WebPageStatus createStatus(String configMapName) {
    WebPageStatus status = new WebPageStatus();
    status.setHtmlConfigMap(configMapName);
    status.setAreWeGood(true);
    status.setErrorMessage(null);
    return status;
  }

  static String configMapName(WebPage nginx) {
    return nginx.getMetadata().getName() + "-html";
  }

  static String deploymentName(WebPage nginx) {
    return nginx.getMetadata().getName();
  }

  static String serviceName(WebPage webPage) {
    return webPage.getMetadata().getName();
  }

  static ErrorStatusUpdateControl<WebPage> handleError(WebPage resource, Exception e) {
    resource.getStatus().setErrorMessage("Error: " + e.getMessage());
    return ErrorStatusUpdateControl.updateStatus(resource);
  }

  static void simulateErrorIfRequested(WebPage webPage) {
    if (webPage.getSpec().getHtml().contains("error")) {
      // special case just to showcase error if doing a demo
      throw new ErrorSimulationException("Simulating error");
    }
  }
}
