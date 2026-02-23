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

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(name = WebappReconciler.NAME)
public class WebappReconciler implements Reconciler<Webapp>, Cleaner<Webapp> {

  private static final Logger log = LoggerFactory.getLogger(WebappReconciler.class);
  public static final String NAME = "webapp";

  private final KubernetesClient kubernetesClient;

  public WebappReconciler(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public List<EventSource<?, Webapp>> prepareEventSources(EventSourceContext<Webapp> context) {
    /*
     * To create an event to a related WebApp resource and trigger the reconciliation we need to
     * find which WebApp this Tomcat custom resource is related to. To find the related
     * customResourceId of the WebApp resource we traverse the cache and identify it based on naming
     * convention.
     */
    final SecondaryToPrimaryMapper<Tomcat> webappsMatchingTomcatName =
        (Tomcat t) ->
            context
                .getPrimaryCache()
                .list(webApp -> webApp.getSpec().getTomcat().equals(t.getMetadata().getName()))
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

    InformerEventSourceConfiguration<Tomcat> configuration =
        InformerEventSourceConfiguration.from(Tomcat.class, Webapp.class)
            .withSecondaryToPrimaryMapper(webappsMatchingTomcatName)
            .withPrimaryToSecondaryMapper(
                (Webapp primary) ->
                    Set.of(
                        new ResourceID(
                            primary.getSpec().getTomcat(), primary.getMetadata().getNamespace())))
            .build();
    return List.of(new InformerEventSource<>(configuration, context));
  }

  /**
   * This method will be called not only on changes to Webapp objects but also when Tomcat objects
   * change.
   */
  @Override
  public UpdateControl<Webapp> reconcile(Webapp webapp, Context<Webapp> context) {
    if (webapp.getStatus() != null
        && Objects.equals(webapp.getSpec().getUrl(), webapp.getStatus().getDeployedArtifact())) {
      return UpdateControl.noUpdate();
    }

    Tomcat tomcat =
        context
            .getSecondaryResource(Tomcat.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot find Tomcat "
                            + webapp.getSpec().getTomcat()
                            + " for Webapp "
                            + webapp.getMetadata().getName()
                            + " in namespace "
                            + webapp.getMetadata().getNamespace()));

    if (tomcat.getStatus() != null
        && Objects.equals(tomcat.getSpec().getReplicas(), tomcat.getStatus().getReadyReplicas())) {
      log.info(
          "Tomcat is ready and webapps not yet deployed. Commencing deployment of {} in Tomcat {}",
          webapp.getMetadata().getName(),
          tomcat.getMetadata().getName());
      String[] command =
          new String[] {
            "wget",
            "-O",
            "/data/" + webapp.getSpec().getContextPath() + ".war",
            webapp.getSpec().getUrl()
          };
      if (log.isInfoEnabled()) {
        command =
            new String[] {
              "time",
              "wget",
              "-O",
              "/data/" + webapp.getSpec().getContextPath() + ".war",
              webapp.getSpec().getUrl()
            };
      }

      String[] commandStatusInAllPods = executeCommandInAllPods(kubernetesClient, webapp, command);

      return UpdateControl.patchStatus(createWebAppForStatusUpdate(webapp, commandStatusInAllPods));
    } else {
      log.info(
          "WebappController invoked but Tomcat not ready yet ({}/{})",
          tomcat.getStatus() != null ? tomcat.getStatus().getReadyReplicas() : 0,
          tomcat.getSpec().getReplicas());
      return UpdateControl.noUpdate();
    }
  }

  private Webapp createWebAppForStatusUpdate(Webapp actual, String[] commandStatusInAllPods) {
    var webapp = new Webapp();
    webapp.setMetadata(
        new ObjectMetaBuilder()
            .withName(actual.getMetadata().getName())
            .withNamespace(actual.getMetadata().getNamespace())
            .build());
    webapp.setStatus(new WebappStatus());
    webapp.getStatus().setDeployedArtifact(actual.getSpec().getUrl());
    webapp.getStatus().setDeploymentStatus(commandStatusInAllPods);
    return webapp;
  }

  @Override
  public DeleteControl cleanup(Webapp webapp, Context<Webapp> context) {

    String[] command = new String[] {"rm", "/data/" + webapp.getSpec().getContextPath() + ".war"};
    String[] commandStatusInAllPods = executeCommandInAllPods(kubernetesClient, webapp, command);
    if (webapp.getStatus() != null) {
      webapp.getStatus().setDeployedArtifact(null);
      webapp.getStatus().setDeploymentStatus(commandStatusInAllPods);
    }
    return DeleteControl.defaultDelete();
  }

  private String[] executeCommandInAllPods(
      KubernetesClient kubernetesClient, Webapp webapp, String[] command) {
    String[] status = new String[0];

    Deployment deployment =
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(webapp.getMetadata().getNamespace())
            .withName(webapp.getSpec().getTomcat())
            .get();

    if (deployment != null) {
      List<Pod> pods =
          kubernetesClient
              .pods()
              .inNamespace(webapp.getMetadata().getNamespace())
              .withLabels(deployment.getSpec().getSelector().getMatchLabels())
              .list()
              .getItems();
      status = new String[pods.size()];
      for (int i = 0; i < pods.size(); i++) {
        Pod pod = pods.get(i);
        log.info(
            "Executing command {} in Pod {}",
            String.join(" ", command),
            pod.getMetadata().getName());

        CompletableFuture<String> data = new CompletableFuture<>();
        try (ExecWatch execWatch = execCmd(pod, data, command)) {
          status[i] = pod.getMetadata().getName() + ":" + data.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          status[i] = pod.getMetadata().getName() + ": ExecutionException - " + e.getMessage();
        } catch (InterruptedException e) {
          status[i] = pod.getMetadata().getName() + ": InterruptedException - " + e.getMessage();
        } catch (TimeoutException e) {
          status[i] = pod.getMetadata().getName() + ": TimeoutException - " + e.getMessage();
        }
      }
    }
    return status;
  }

  private ExecWatch execCmd(Pod pod, CompletableFuture<String> data, String... command) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    return kubernetesClient
        .pods()
        .inNamespace(pod.getMetadata().getNamespace())
        .withName(pod.getMetadata().getName())
        .inContainer("war-downloader")
        .writingOutput(baos)
        .writingError(baos)
        .usingListener(new SimpleListener(data, baos))
        .exec(command);
  }

  static class SimpleListener implements ExecListener {

    private final CompletableFuture<String> data;
    private final ByteArrayOutputStream baos;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream baos) {
      this.data = data;
      this.baos = baos;
    }

    @Override
    public void onOpen() {
      log.debug("Reading data... ");
    }

    @Override
    public void onFailure(Throwable t, Response response) {
      log.debug(t.getMessage());
      data.completeExceptionally(t);
    }

    @Override
    public void onClose(int code, String reason) {
      log.debug("Exit with: {} and with reason: {}", code, reason);
      data.complete(baos.toString());
    }
  }
}
