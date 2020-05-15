package com.github.containersolutions.operator.sample;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;

import io.fabric8.kubernetes.api.model.DoneableNamespace;
import io.fabric8.kubernetes.api.model.DoneableSecret;
import io.fabric8.kubernetes.api.model.DoneableService;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.client.utils.URLFromServiceUtil;

@Controller(customResourceClass = ObjectStore.class,
        crdName = "objectstores.sample.javaoperatorsdk")
public class ObjectStoreController implements ResourceController<ObjectStore> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubernetesClient;

    public ObjectStoreController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public Optional<ObjectStore> createOrUpdateResource(ObjectStore objectStore) {
        String osNS = objectStore.getMetadata().getNamespace();
        log.info("Objectstore namespace {}", osNS);
        
        String ns = deploymentNameSpace(objectStore);
        Namespace namespace = loadYaml(Namespace.class, "namespace.yaml");
        namespace.getMetadata().setName(ns);
        if (kubernetesClient.namespaces().withName(namespace.getMetadata().getName()).get() == null) {
            log.info("Creating Namespace {}", ns);
            kubernetesClient.namespaces().createOrReplace(namespace);
        }

        MinioInstance minioInstance = loadYaml(MinioInstance.class, "minioinstance.yaml");
        minioInstance.getMetadata().setName(deploymentName(objectStore));
        minioInstance.getMetadata().setNamespace(ns);
        minioInstance.getSpec().getSelector().getMatchLabels().put("app", deploymentName(objectStore));

        Service service = loadYaml(Service.class, "service.yaml");
        service.getMetadata().setName(serviceName(objectStore));
        service.getMetadata().setNamespace(ns);
        service.getSpec().setSelector(minioInstance.getSpec().getSelector().getMatchLabels());
        if (kubernetesClient.services().inNamespace(ns).withName(service.getMetadata().getName()).get() == null) {
            log.info("Creating Service {} in {}", service.getMetadata().getName(), ns);
            kubernetesClient.services().inNamespace(ns).createOrReplace(service);
        }
        
        Secret secret = loadYaml(Secret.class, "secret.yaml");
        secret.getMetadata().setName(secretName(objectStore));
        secret.getMetadata().setNamespace(ns);
        if (kubernetesClient.secrets().inNamespace(ns).withName(secret.getMetadata().getName()).get() == null) {
            log.info("Creating Secret {} in {}", secret.getMetadata().getName(), ns);
            kubernetesClient.secrets().inNamespace(ns).createOrReplace(secret);
        }

        log.info("Creating or updating MinioInstance {} in {}", minioInstance.getMetadata().getName(), ns);
        
        minioInstance.getSpec().setServiceName(serviceName((objectStore)));
        minioInstance.getSpec().getCredsSecret().setName(secretName(objectStore));
        minioInstance.getSpec().getZones().add(objectStore.getSpec().getInstances().getZone());
        minioInstance.getSpec().setVolumesPerServer(objectStore.getSpec().getVolumes().getCount());
        minioInstance.getSpec().getVolumeClaimTemplate().getSpec().getResources().getRequests().put(
                "storage", new Quantity(objectStore.getSpec().getVolumes().getSize()));
        minioInstance.getSpec().getResources().getRequests().put(
                "memory", new Quantity(objectStore.getSpec().getInstances().getZone().getMem()));
        minioInstance.getSpec().getResources().getRequests().put(
                "cpu", new Quantity(objectStore.getSpec().getInstances().getZone().getCpu()));
        
        CustomResourceDefinition minioOperatorCrd = getMinioOperatorCRD();
        NonNamespaceOperation<MinioInstance, MinioInstanceList, DoneableMinioInstance, Resource<MinioInstance, DoneableMinioInstance>> minioInstanceClient = 
                kubernetesClient.customResources(minioOperatorCrd, MinioInstance.class, MinioInstanceList.class, DoneableMinioInstance.class);
        minioInstanceClient = 
                ((MixedOperation<MinioInstance, MinioInstanceList, DoneableMinioInstance, Resource<MinioInstance, DoneableMinioInstance>>) minioInstanceClient).inNamespace(ns);
        minioInstanceClient.createOrReplace(minioInstance);

        ServicePort port = URLFromServiceUtil.getServicePortByName(service, "http-minio");
        IntOrString targetPort = port.getTargetPort();
        String serviceProto = port.getProtocol();
        String clusterIP = (String) kubernetesClient.services().inNamespace(ns).withName(serviceName(objectStore)).get().getSpec().getClusterIP();
        String serviceURL = (serviceProto + "://" + clusterIP + ":" + targetPort.getIntVal()).toLowerCase(Locale.ROOT);
        log.info("Accesing service url {} of MinioInstance {} in {}", serviceURL, minioInstance.getMetadata().getName(), ns);
        
        ObjectStoreStatus status = new ObjectStoreStatus();
        status.setStatus("Yes!");
        objectStore.setStatus(status);
        return Optional.of(objectStore);
    }

    @Override
    public boolean deleteResource(ObjectStore objectStore) {
        String ns = deploymentNameSpace(objectStore);

        log.info("Deleting MinioInstance {}", deploymentName(objectStore));
        CustomResourceDefinition minioOperatorCrd = getMinioOperatorCRD();
        NonNamespaceOperation<MinioInstance, MinioInstanceList, DoneableMinioInstance, Resource<MinioInstance, DoneableMinioInstance>> minioInstanceClient = 
                kubernetesClient.customResources(minioOperatorCrd, MinioInstance.class, MinioInstanceList.class, DoneableMinioInstance.class);
        minioInstanceClient = 
                ((MixedOperation<MinioInstance, MinioInstanceList, DoneableMinioInstance, Resource<MinioInstance, DoneableMinioInstance>>) minioInstanceClient).inNamespace(ns);
        MinioInstance minioInstance = getMinioInstance(objectStore, minioInstanceClient.list());
        
        if (minioInstance != null) {
            minioInstanceClient.delete(minioInstance);
        }

        log.info("Deleting Service {}", serviceName(objectStore));
        ServiceResource<Service, DoneableService> service = kubernetesClient.services()
                .inNamespace(ns)
                .withName(serviceName(objectStore));
        if (service.get() != null) {
            service.delete();
        }
        
        log.info("Deleting Secret {}", secretName(objectStore));
        Resource<Secret, DoneableSecret> secret = kubernetesClient.secrets()
                .inNamespace(ns)
                .withName(secretName(objectStore));
        if (secret.get() != null) {
            secret.delete();
        }
        
        log.info("Deleting namespace {}", ns);
        Resource<Namespace, DoneableNamespace> namespace = kubernetesClient.namespaces()
                .withName(ns);
        if (namespace.get() != null) {
            namespace.delete();
        }
        return true;
    }

    private MinioInstance getMinioInstance(ObjectStore objectStore, CustomResourceList<MinioInstance> minioInstanceList) {
        List<MinioInstance> items = minioInstanceList.getItems();
        MinioInstance minioInstance = null;
        for (MinioInstance item : items) {
            if (item.getMetadata() != null) {
                if (deploymentName(objectStore).equals(item.getMetadata().getName())) {
                    minioInstance = item;
                    break;
                }
            }
        }
        return minioInstance;
    }
    
    private CustomResourceDefinition getMinioOperatorCRD() {
        CustomResourceDefinitionList crds = kubernetesClient.customResourceDefinitions().list();
        List<CustomResourceDefinition> crdsItems = crds.getItems();
        CustomResourceDefinition minioOperatorCrd = null;
        for (CustomResourceDefinition crd : crdsItems) {
            ObjectMeta metadata = crd.getMetadata();
            if (metadata != null) {
                String name = metadata.getName();
                if ("minioinstances.operator.min.io".equals(name)) {
                    minioOperatorCrd = crd;
                    break;
                }
            }
        }
        return minioOperatorCrd;
    }
    

    private String deploymentNameSpace(ObjectStore minioOp) {
        return minioOp.getSpec().getDeployNamespace();
    }
    
    private String deploymentName(ObjectStore minioOp) {
        return minioOp.getSpec().getName();
    }

    private String serviceName(ObjectStore minioOp) {
        return minioOp.getSpec().getName() + "-service";
    }
    
    private String secretName(ObjectStore minioOp) {
        return minioOp.getSpec().getName() + "-secret";
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }
}
