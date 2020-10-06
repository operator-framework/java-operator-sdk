package com.example.jaxdemo;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DoneableDeployment;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller(customResourceClass = Tomcat.class,
        crdName = "tomcats.tomcatoperator.io")
public class TomcatController implements ResourceController<Tomcat> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubernetesClient;

    private MixedOperation<Tomcat, CustomResourceList<Tomcat>, CustomResourceDoneable<Tomcat>, Resource<Tomcat, CustomResourceDoneable<Tomcat>>> tomcatOperations;

    private final List<Object> watchedResources = new ArrayList<>();

    public TomcatController(KubernetesClient client) {
        this.kubernetesClient = client;
    }

    private void updateTomcatStatus(Context<Tomcat> context, Tomcat tomcat, Deployment deployment) {
        int readyReplicas = Objects.requireNonNullElse(deployment.getStatus().getReadyReplicas(), 0);
        log.info("Updating status of Tomcat {} in namespace {} to {} ready replicas", tomcat.getMetadata().getName(),
                tomcat.getMetadata().getNamespace(), readyReplicas);

        tomcatOperations
                .inNamespace(tomcat.getMetadata().getNamespace())
                .withName(tomcat.getMetadata().getName())
                .replace(tomcat);
    }

    @Override
    public UpdateControl<Tomcat> createOrUpdateResource(Tomcat tomcat, Context<Tomcat> context) {
        Deployment deployment = createOrUpdateDeployment(tomcat);
        createOrUpdateService(tomcat);

        if (!watchedResources.contains(WatchedResource.fromResource(deployment))) {
            log.info("Attaching Watch to Deployment {}", deployment.getMetadata().getName());
            kubernetesClient.apps().deployments().withName(deployment.getMetadata().getName()).watch(new Watcher<Deployment>() {
                @Override
                public void eventReceived(Action action, Deployment deployment) {
                    try {
                        Tomcat tomcat = tomcatOperations.inNamespace(deployment.getMetadata().getNamespace())
                                .withName(deployment.getMetadata().getLabels().get("created-by")).get();
                        updateTomcatStatus(context, tomcat, deployment);
                    } catch (Exception ex) {
                        log.error(ex.getMessage());
                    }
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                }
            });
            watchedResources.add(WatchedResource.fromResource(deployment));
        }


        return UpdateControl.noUpdate();
    }

    @Override
    public boolean deleteResource(Tomcat tomcat, Context<Tomcat> context) {
        deleteDeployment(tomcat);
        deleteService(tomcat);
        return true;
    }

    private Deployment createOrUpdateDeployment(Tomcat tomcat) {
        String ns = tomcat.getMetadata().getNamespace();
        Deployment existingDeployment = kubernetesClient.apps().deployments()
                .inNamespace(ns).withName(tomcat.getMetadata().getName())
                .get();
        if (existingDeployment == null) {
            Deployment deployment = loadYaml(Deployment.class, "deployment.yaml");
            deployment.getMetadata().setName(tomcat.getMetadata().getName());
            deployment.getMetadata().setNamespace(ns);
            deployment.getMetadata().getLabels().put("created-by", tomcat.getMetadata().getName());
            // set tomcat version
            deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage("tomcat:" + tomcat.getSpec().getVersion());
            deployment.getSpec().setReplicas(tomcat.getSpec().getReplicas());

            //make sure label selector matches label (which has to be matched by service selector too)
            deployment.getSpec().getTemplate().getMetadata().getLabels().put("app", tomcat.getMetadata().getName());
            deployment.getSpec().getSelector().getMatchLabels().put("app", tomcat.getMetadata().getName());

            log.info("Creating or updating Deployment {} in {}", deployment.getMetadata().getName(), ns);
            return kubernetesClient.apps().deployments().inNamespace(ns).create(deployment);
        } else {
            existingDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).setImage("tomcat:" + tomcat.getSpec().getVersion());
            existingDeployment.getSpec().setReplicas(tomcat.getSpec().getReplicas());
            return kubernetesClient.apps().deployments().inNamespace(ns).createOrReplace(existingDeployment);
        }
    }

    private void deleteDeployment(Tomcat tomcat) {
        log.info("Deleting Deployment {}", tomcat.getMetadata().getName());
        RollableScalableResource<Deployment, DoneableDeployment> deployment = kubernetesClient.apps().deployments()
                .inNamespace(tomcat.getMetadata().getNamespace())
                .withName(tomcat.getMetadata().getName());
        if (deployment.get() != null) {
            deployment.delete();
        }
    }

    private void createOrUpdateService(Tomcat tomcat) {
        Service service = loadYaml(Service.class, "service.yaml");
        service.getMetadata().setName(tomcat.getMetadata().getName());
        String ns = tomcat.getMetadata().getNamespace();
        service.getMetadata().setNamespace(ns);
        service.getSpec().getSelector().put("app", tomcat.getMetadata().getName());
        log.info("Creating or updating Service {} in {}", service.getMetadata().getName(), ns);
        kubernetesClient.services().inNamespace(ns).createOrReplace(service);
    }

    private void deleteService(Tomcat tomcat) {
        log.info("Deleting Service {}", tomcat.getMetadata().getName());
        ServiceResource<Service, DoneableService> service = kubernetesClient.services()
                .inNamespace(tomcat.getMetadata().getNamespace())
                .withName(tomcat.getMetadata().getName());
        if (service.get() != null) {
            service.delete();
        }
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }

    public void setTomcatOperations(MixedOperation<Tomcat, CustomResourceList<Tomcat>, CustomResourceDoneable<Tomcat>, Resource<Tomcat, CustomResourceDoneable<Tomcat>>> tomcatOperations) {
        this.tomcatOperations = tomcatOperations;
    }

    private static class WatchedResource {
        private final String type;
        private final String name;

        public WatchedResource(String type, String name) {
            this.type = type;
            this.name = name;
        }

        public static WatchedResource fromResource(HasMetadata resource) {
            return new WatchedResource(resource.getKind(), resource.getMetadata().getName());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            WatchedResource that = (WatchedResource) o;

            return new EqualsBuilder()
                    .append(type, that.type)
                    .append(name, that.name)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }

        @Override
        public String toString() {
            return "WatchedResource{" +
                    "type='" + type + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}