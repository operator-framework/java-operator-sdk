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
package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;
import io.javaoperatorsdk.operator.sample.customresource.WebPageStatus;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;

public class Utils {

  private Utils() {}

  public static WebPage createWebPageForStatusUpdate(WebPage webPage, String configMapName) {
    WebPage res = new WebPage();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(webPage.getMetadata().getName())
            .withNamespace(webPage.getMetadata().getNamespace())
            .build());
    res.setStatus(createStatus(configMapName));
    return res;
  }

  public static WebPageStatus createStatus(String configMapName) {
    WebPageStatus status = new WebPageStatus();
    status.setHtmlConfigMap(configMapName);
    status.setAreWeGood(true);
    status.setErrorMessage(null);
    return status;
  }

  public static String configMapName(WebPage nginx) {
    return nginx.getMetadata().getName() + "-html";
  }

  public static String deploymentName(WebPage nginx) {
    return nginx.getMetadata().getName();
  }

  public static String serviceName(WebPage webPage) {
    return webPage.getMetadata().getName();
  }

  public static ErrorStatusUpdateControl<WebPage> handleError(WebPage resource, Exception e) {
    resource.getStatus().setErrorMessage("Error: " + e.getMessage());
    return ErrorStatusUpdateControl.patchStatus(resource);
  }

  public static void simulateErrorIfRequested(WebPage webPage) throws ErrorSimulationException {
    if (webPage.getSpec().getHtml().contains("error")) {
      // special case just to showcase error if doing a demo
      throw new ErrorSimulationException("Simulating error");
    }
  }

  public static boolean isValidHtml(WebPage webPage) {
    // very dummy html validation
    var lowerCaseHtml = webPage.getSpec().getHtml().toLowerCase();
    return lowerCaseHtml.contains("<html>") && lowerCaseHtml.contains("</html>");
  }

  public static WebPage setInvalidHtmlErrorMessage(WebPage webPage) {
    if (webPage.getStatus() == null) {
      webPage.setStatus(new WebPageStatus());
    }
    webPage.getStatus().setErrorMessage("Invalid html.");
    return webPage;
  }

  public static Ingress makeDesiredIngress(WebPage webPage) {
    Ingress ingress = loadYaml(Ingress.class, Utils.class, "ingress.yaml");
    ingress.getMetadata().setName(webPage.getMetadata().getName());
    ingress.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    ingress
        .getSpec()
        .getRules()
        .get(0)
        .getHttp()
        .getPaths()
        .get(0)
        .getBackend()
        .getService()
        .setName(serviceName(webPage));
    return ingress;
  }
}
