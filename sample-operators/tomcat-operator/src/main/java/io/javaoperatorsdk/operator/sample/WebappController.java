package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Controller
public class WebappController implements ResourceController<Webapp> {

  private KubernetesClient kubernetesClient;

  private final Logger log = LoggerFactory.getLogger(getClass());

  public WebappController(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public void init(EventSourceManager eventSourceManager) {
    TomcatEventSource tomcatEventSource = TomcatEventSource.createAndRegisterWatch(kubernetesClient);
    eventSourceManager.registerEventSource("tomcat-event-source", tomcatEventSource);
  }

  /**
   * This method will be called not only on changes to Webapp objects but also when Tomcat objects change.
   */
  @Override
  public UpdateControl<Webapp> createOrUpdateResource(Webapp webapp, Context<Webapp> context) {
    if (webapp.getStatus() != null && Objects.equals(webapp.getSpec().getUrl(), webapp.getStatus().getDeployedArtifact())) {
      return UpdateControl.noUpdate();
    }

    var tomcatClient = kubernetesClient.customResources(Tomcat.class);
    Tomcat tomcat = tomcatClient.inNamespace(webapp.getMetadata().getNamespace()).withName(webapp.getSpec().getTomcat()).get();
    if (tomcat == null) {
      throw new IllegalStateException("Cannot find Tomcat " + webapp.getSpec().getTomcat() + " for Webapp " + webapp.getMetadata().getName() + " in namespace " + webapp.getMetadata().getNamespace());
    }

    if (tomcat.getStatus() != null && Objects.equals(tomcat.getSpec().getReplicas(), tomcat.getStatus().getReadyReplicas())) {
      log.info("Tomcat is ready and webapps not yet deployed. Commencing deployment of {} in Tomcat {}", webapp.getMetadata().getName(), tomcat.getMetadata().getName());
      String[] command = new String[]{"wget", "-O", "/data/" + webapp.getSpec().getContextPath() + ".war", webapp.getSpec().getUrl()};
      if(log.isInfoEnabled()){
        command = new String[]{"time", "wget", "-O", "/data/" + webapp.getSpec().getContextPath() + ".war", webapp.getSpec().getUrl()};
      }

      String[] commandStatusInAllPods = executeCommandInAllPods(kubernetesClient, webapp, command);

      if (webapp.getStatus() == null) {
        webapp.setStatus(new WebappStatus());
      }
      webapp.getStatus().setDeployedArtifact(webapp.getSpec().getUrl());
      webapp.getStatus().setDeploymentStatus(commandStatusInAllPods);
      return UpdateControl.updateStatusSubResource(webapp);
    } else {
      log.info("WebappController invoked but Tomcat not ready yet ({}/{})",
              tomcat.getStatus() != null ? tomcat.getStatus().getReadyReplicas() : 0, tomcat.getSpec().getReplicas());
      return UpdateControl.noUpdate();
    }
  }

  @Override
  public DeleteControl deleteResource(Webapp webapp, Context<Webapp> context) {

    String[] command = new String[] {"rm", "/data/" + webapp.getSpec().getContextPath() + ".war"};
    String[] commandStatusInAllPods = executeCommandInAllPods(kubernetesClient, webapp, command);
    if (webapp.getStatus() != null) {
      webapp.getStatus().setDeployedArtifact(null);
      webapp.getStatus().setDeploymentStatus(commandStatusInAllPods);
    }
    return DeleteControl.DEFAULT_DELETE;
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
      for (int i=0; i<pods.size();i++) {
        Pod pod = pods.get(i);
        log.info(
            "Executing command {} in Pod {}",
            String.join(" ", command),
            pod.getMetadata().getName());

        CompletableFuture<String> data = new CompletableFuture<>();
        try (ExecWatch execWatch = execCmd(pod, data, command)) {
          status[i] = ""+pod.getMetadata().getName()+":"+data.get(30, TimeUnit.SECONDS);;
        } catch (ExecutionException e) {
          status[i] = ""+pod.getMetadata().getName()+": ExecutionException - "+e.getMessage();
        } catch (InterruptedException e) {
          status[i] = ""+pod.getMetadata().getName()+": InterruptedException - "+e.getMessage();
        } catch (TimeoutException e) {
          status[i] = ""+pod.getMetadata().getName()+": TimeoutException - "+e.getMessage();
        }
      }
    }
    return status;
  }

  private ExecWatch execCmd(Pod pod, CompletableFuture<String> data, String... command) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    return kubernetesClient.pods()
            .inNamespace(pod.getMetadata().getNamespace())
            .withName(pod.getMetadata().getName())
            .inContainer("war-downloader")
            .writingOutput(baos)
            .writingError(baos)
            .usingListener(new SimpleListener(data, baos))
            .exec(command);
  }

  static class SimpleListener implements ExecListener {

    private CompletableFuture<String> data;
    private ByteArrayOutputStream baos;
    private final Logger log = LoggerFactory.getLogger(getClass());
    public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream baos) {
      this.data = data;
      this.baos = baos;
    }

    @Override
    public void onOpen(Response response) {
      log.debug("Reading data... " + response.message());
    }

    @Override
    public void onFailure(Throwable t, Response response) {
      log.debug(t.getMessage() + " " + response.message());
      data.completeExceptionally(t);
    }

    @Override
    public void onClose(int code, String reason) {
      log.debug("Exit with: " + code + " and with reason: " + reason);
      data.complete(baos.toString());
    }
  }

}
