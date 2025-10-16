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
package io.javaoperatorsdk.operator.baseapi.expectation;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.expectation.Expectation;
import io.javaoperatorsdk.operator.processing.expectation.ExpectationManager;

@ControllerConfiguration(triggerReconcilerOnAllEvent = true)
public class ExpectationReconciler implements Reconciler<ExpectationCustomResource> {

  public static final String DEPLOYMENT_READY = "Deployment ready";
  public static final String DEPLOYMENT_TIMEOUT = "Deployment timeout";
  public static final String DEPLOYMENT_READY_EXPECTATION_NAME = "deploymentReadyExpectation";
  private final ExpectationManager<ExpectationCustomResource> expectationManager =
      new ExpectationManager<>();

  private volatile Long timeout = 30000l;

  @Override
  public UpdateControl<ExpectationCustomResource> reconcile(
      ExpectationCustomResource primary, Context<ExpectationCustomResource> context) {

    // cleans up expectation manager for the resource on delete event
    // in case of cleaner interface used, this can done also there.
    if (context.isPrimaryResourceDeleted()) {
      expectationManager.cleanup(primary);
    }

    // exiting asap if there is an expectation that is not timed out neither fulfilled
    if (expectationManager.ongoingExpectationPresent(primary, context)) {
      return UpdateControl.noUpdate();
    }

    var deployment = context.getSecondaryResource(Deployment.class);
    if (deployment.isEmpty()) {
      createDeployment(primary, context);
      expectationManager.setExpectation(
          primary, Duration.ofSeconds(timeout), deploymentReadyExpectation(context));
      return UpdateControl.noUpdate();
    } else {
      // checks the expectation if it is fulfilled also removes it,
      // in your logic you might add a next expectation based on your workflow
      // Expectations have a name, so you can easily distinguish them if there is more of them.
      var res =
          expectationManager.checkExpectation(DEPLOYMENT_READY_EXPECTATION_NAME, primary, context);
      // Note that this happens only once, since if the expectation is fulfilled, it is also removed
      // from the manager.
      if (res.isFulfilled()) {
        return pathchStatusWithMessage(primary, DEPLOYMENT_READY);
      } else if (res.isTimedOut()) {
        // you might add some other timeout handling here
        return pathchStatusWithMessage(primary, DEPLOYMENT_TIMEOUT);
      }
    }
    return UpdateControl.noUpdate();
  }

  private static UpdateControl<ExpectationCustomResource> pathchStatusWithMessage(
      ExpectationCustomResource primary, String message) {
    primary.setStatus(new ExpectationCustomResourceStatus());
    primary.getStatus().setMessage(message);
    return UpdateControl.patchStatus(primary);
  }

  private static Expectation<ExpectationCustomResource> deploymentReadyExpectation(
      Context<ExpectationCustomResource> context) {
    return Expectation.createExpectation(
        DEPLOYMENT_READY_EXPECTATION_NAME,
        (p, c) -> {
          var actualDeployment = context.getSecondaryResource(Deployment.class).orElseThrow();
          return actualDeployment.getStatus() != null
              && actualDeployment.getStatus().getReadyReplicas() != null
              && actualDeployment.getStatus().getReadyReplicas() == 3;
        });
  }

  private Deployment createDeployment(
      ExpectationCustomResource primary, Context<ExpectationCustomResource> context) {
    var d =
        new DeploymentBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .withSpec(
                new DeploymentSpecBuilder()
                    .withReplicas(3)
                    .withSelector(
                        new LabelSelectorBuilder().withMatchLabels(Map.of("app", "nginx")).build())
                    .withTemplate(
                        new PodTemplateSpecBuilder()
                            .withMetadata(
                                new ObjectMetaBuilder().withLabels(Map.of("app", "nginx")).build())
                            .withSpec(
                                new PodSpecBuilder()
                                    .withContainers(
                                        new ContainerBuilder()
                                            .withName("nginx")
                                            .withImage("nginx:1.29.2")
                                            .withPorts(
                                                new ContainerPortBuilder()
                                                    .withContainerPort(80)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();
    d.addOwnerReference(primary);
    return context.getClient().resource(d).serverSideApply();
  }

  @Override
  public List<EventSource<?, ExpectationCustomResource>> prepareEventSources(
      EventSourceContext<ExpectationCustomResource> context) {
    return List.of(
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(Deployment.class, ExpectationCustomResource.class)
                .build(),
            context));
  }

  public void setTimeout(Long timeout) {
    this.timeout = timeout;
  }
}
