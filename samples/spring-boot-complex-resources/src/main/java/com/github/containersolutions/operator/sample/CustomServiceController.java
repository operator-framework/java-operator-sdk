package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.api.Context;

import io.fabric8.kubernetes.client.dsl.Resource;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A very simple sample controller that creates a service with a label.
 */
@Controller(customResourceClass = CustomService.class, crdName = "customservices.sample.javaoperatorsdk")
public class CustomServiceController implements ResourceController<CustomService> {

	public static final String KIND = "CustomService";
	private final static Logger log = LoggerFactory.getLogger(CustomServiceController.class);

	private final KubernetesClient kubernetesClient;

	private Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> customResourceClient;

	public CustomServiceController(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public boolean deleteResource(CustomService resource, Context<CustomService> context) {
		log.info("Execution deleteResource for: {}", resource.getMetadata().getName());
		kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace())
				.withName(resource.getMetadata().getName()).delete();
		return true;
	}

	/***
	 * recap: create ALL dependent deployments. Register for ALL. Collect ready
	 * states or some state after receiving events create MAIN deployment. Register
	 * for it. on success, dereg. create TEST deployment. Register for it. on
	 * result, update own ready status.
	 */

	private static boolean isDeploymentReady(Deployment d) {
		return d != null && d.getStatus() != null && d.getStatus().getReadyReplicas() != null
				&& d.getStatus().getAvailableReplicas() != null
				&& d.getStatus().getReadyReplicas() == d.getStatus().getAvailableReplicas()
				&& d.getStatus().getReadyReplicas() > 0;
	}

	@Override
	public UpdateControl<CustomService> createOrUpdateResource(CustomService resource, Context<CustomService> context) {
		log.info("Execution createOrUpdateResource for: {}", resource.getMetadata().getName());

		String ns = resource.getMetadata().getNamespace();
		String resourceName = resource.getMetadata().getName();

		ArrayList<String> deploymentNames = new ArrayList<String>();
		for (ServiceDependency dep : resource.getSpec().getDependencies()) {
			deploymentNames.add(resourceName + "-" + dep.getName());
		}

		Watcher<Deployment> testWatcher = new Watcher<Deployment>() {

			@Override
			public void eventReceived(Action action, Deployment watchedResource) {
				// has the main deployment finished?
				if (isDeploymentReady(watchedResource)) {
					MixedOperation<?, ?, ?, Resource<CustomResource, ?>> x = (MixedOperation<?, ?, ?, Resource<CustomResource, ?>>) customResourceClient
							.get(CustomService.class);
					CustomService r = (CustomService) x.inNamespace(resource.getMetadata().getNamespace())
							.withName(resource.getMetadata().getName()).get();
					r.setStatus(new ServiceStatus());
					r.getStatus().setAdditionalProperty("ready", true);
					x.inNamespace(ns).withName(resource.getMetadata().getName()).updateStatus(r);
				}
			}

			@Override
			public void onClose(KubernetesClientException cause) {
			}

		};
		String testDeploymentName = resourceName + "-" + resource.getSpec().getService().getName() + "testing";

		Watcher<Deployment> mainWatcher = new Watcher<Deployment>() {

			@Override
			public void eventReceived(Action action, Deployment watchedResource) {
				// has the main deployment finished?
				if (isDeploymentReady(watchedResource)) {
					// "run" test suite
					createDeployment(ns, testDeploymentName, resource.getSpec().getTestSuite(), resource, testWatcher);
					// unregister watcher?
				}
			}

			@Override
			public void onClose(KubernetesClientException cause) {
			}

		};
		String mainDeploymentName = resourceName + "-" + resource.getSpec().getService().getName();

		Watcher<Deployment> dependentWatcher = new Watcher<Deployment>() {

			@Override
			public void eventReceived(Action action, Deployment watchedResource) {
					// has this deployment become ready?
					if (isDeploymentReady(watchedResource)) {
						boolean readyForMain = true;
						for (String dep : deploymentNames) {
							if (!isDeploymentReady(
									kubernetesClient.apps().deployments().inNamespace(ns).withName(dep).get())) {
								// check all deployments are ready
								readyForMain = false;
							}
						}
						if (readyForMain) {
							// create main deployment
							createDeployment(ns, mainDeploymentName, resource.getSpec().getService().getImage(),
									resource, mainWatcher);
						}
					}
			}

			@Override
			public void onClose(KubernetesClientException cause) {
			}
		};

		// create dependent deployments
		boolean dependenciesAllDone = true;
		for (ServiceDependency dep : resource.getSpec().getDependencies()) {
			String name = resourceName + "-" + dep.getName();
			String image = dep.getImage();
			if (kubernetesClient.apps().deployments().inNamespace(ns).withName(name).get() == null) {
				createDeployment(ns, name, image, resource, dependentWatcher);
				dependenciesAllDone = false;
			}
		}
		if (dependenciesAllDone) {
			// create main deployment
			if (kubernetesClient.apps().deployments().inNamespace(ns).withName(mainDeploymentName).get() == null) {
				createDeployment(ns, mainDeploymentName, resource.getSpec().getService().getImage(), resource,
						mainWatcher);
			} else {
				if (kubernetesClient.apps().deployments().inNamespace(ns).withName(testDeploymentName).get() == null) {
					// "run" test suite
					createDeployment(ns, testDeploymentName, resource.getSpec().getTestSuite(), resource, testWatcher);
				} else {
					if (resource.getStatus() == null
							|| resource.getStatus().getAdditionalProperties().get("ready") == null
							|| resource.getStatus().getAdditionalProperties().get("ready") != new Boolean(true)) {
						MixedOperation<?, ?, ?, Resource<CustomResource, ?>> x = (MixedOperation<?, ?, ?, Resource<CustomResource, ?>>) customResourceClient
								.get(CustomService.class);
						CustomService r = (CustomService) x.inNamespace(resource.getMetadata().getNamespace())
								.withName(resource.getMetadata().getName()).get();
						r.setStatus(new ServiceStatus());
						r.getStatus().setAdditionalProperty("ready", true);
						x.inNamespace(ns).withName(resource.getMetadata().getName()).updateStatus(r);
					}
				}
			}
		}

		return UpdateControl.noUpdate();
	}

	private void createDeployment(String ns, String name, String image, CustomResource ownerResource,
			Watcher<Deployment> watcher) {
		ArrayList<ContainerPort> ports = new ArrayList<ContainerPort>();
		ports.add(new ContainerPortBuilder().withContainerPort(3000).build());
		ArrayList<Container> containers = new ArrayList<Container>();
		containers.add(new ContainerBuilder().withName(name).withImage(image).withPorts(ports).build());
		Map<String, String> labels = new HashMap<>();
		labels.put("app", name);
		kubernetesClient.apps().deployments().inNamespace(ns).createOrReplaceWithNew().withNewMetadata().withName(name)
				.withOwnerReferences((new OwnerReferenceBuilder()).withApiVersion(ownerResource.getApiVersion())
						.withKind(ownerResource.getKind()).withController(true)
						.withName(ownerResource.getMetadata().getName()).withUid(ownerResource.getMetadata().getUid())
						.build())
				.endMetadata().withNewSpec().withReplicas(3).withNewSelector().withMatchLabels(labels).endSelector()
				.withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec()
				.withContainers(containers).endSpec().endTemplate().endSpec().done();
		kubernetesClient.v1().events().createNew().withNewMetadata().withName(java.util.UUID.randomUUID().toString())
				.withNamespace(ns).endMetadata().withNewAction("Deployment Creation")
				.withNewMessage("created deployment " + name).withNewInvolvedObject().withNamespace(ns)
				.withKind(ownerResource.getKind()).withName(ownerResource.getMetadata().getName())
				.withNewUid(ownerResource.getMetadata().getUid())
				.withNewResourceVersion(ownerResource.getMetadata().getResourceVersion()).endInvolvedObject().done();
		kubernetesClient.apps().deployments().inNamespace(ns).withName(name).watch(watcher);
	}

	public void setCustomResourceClient(
			Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> customResourceClients) {
		this.customResourceClient = customResourceClients;

	}
}
