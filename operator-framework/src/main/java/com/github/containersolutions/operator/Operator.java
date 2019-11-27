package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.processing.EventDispatcher;
import com.github.containersolutions.operator.processing.EventScheduler;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.containersolutions.operator.ControllerUtils.*;

public class Operator {

    private final KubernetesClient k8sClient;

    private Map<ResourceController, EventScheduler> controllers = new HashMap<>();
    private Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> customResourceClients = new HashMap<>();
    private EventScheduler eventScheduler;

    private final static Logger log = LoggerFactory.getLogger(Operator.class);

    public Operator(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
    }

    public <R extends CustomResource> void registerControllerForAllNamespaces(ResourceController<R> controller) throws OperatorException {
        registerController(controller, true);
    }

    public <R extends CustomResource> void registerController(ResourceController<R> controller, String... targetNamespaces) throws OperatorException {
        registerController(controller, false, targetNamespaces);
    }

    private <R extends CustomResource> void registerController(ResourceController<R> controller,
                                                               boolean watchAllNamespaces, String... targetNamespaces) throws OperatorException {
        Class<R> resClass = getCustomResourceClass(controller);
        Optional<CustomResourceDefinition> crd = getCustomResourceDefinitionForController(controller);
        String kind = ControllerUtils.getKind(controller);
        KubernetesDeserializer.registerCustomKind(getApiVersion(controller), kind, resClass);

        if (crd.isPresent()) {
            Class<? extends CustomResourceList<R>> list = getCustomResourceListClass(controller);
            Class<? extends CustomResourceDoneable<R>> doneable = getCustomResourceDonebaleClass(controller);
            MixedOperation client = k8sClient.customResources(crd.get(), resClass, list, doneable);

            EventDispatcher<R> eventDispatcher =
                    new EventDispatcher<>(controller, (CustomResourceOperationsImpl) client, client, k8sClient,
                            ControllerUtils.getDefaultFinalizer(controller));

            eventScheduler = new EventScheduler(eventDispatcher);

            eventScheduler.startProcessing();

            registerWatches(controller, client, resClass, watchAllNamespaces, targetNamespaces);
        } else {
            throw new OperatorException("CRD '" + resClass.getSimpleName() + "' with version '"
                    + getVersion(controller) + "' not found");
        }
    }

    private <R extends CustomResource> void registerWatches(ResourceController<R> controller, MixedOperation client,
                                                            Class<R> resClass,
                                                            boolean watchAllNamespaces, String[] targetNamespaces) {
        CustomResourceOperationsImpl crClient = (CustomResourceOperationsImpl) client;
        if (watchAllNamespaces) {
            crClient.inAnyNamespace().watch(eventScheduler);
        } else if (targetNamespaces.length == 0) {
            client.watch(eventScheduler);
        } else {
            for (String targetNamespace : targetNamespaces) {
                crClient.inNamespace(targetNamespace).watch(eventScheduler);
                log.debug("Registered controller for namespace: {}", targetNamespace);
            }
        }
        customResourceClients.put(resClass, (CustomResourceOperationsImpl) client);
        controllers.put(controller, eventScheduler);
        log.info("Registered Controller: '{}' for CRD: '{}' for namespaces: {}", controller.getClass().getSimpleName(),
                resClass, targetNamespaces.length == 0 ? "[all/client namespace]" : Arrays.toString(targetNamespaces));
    }

    private Optional<CustomResourceDefinition> getCustomResourceDefinitionForController(ResourceController controller) {
        Optional<String> crdName = getCrdName(controller);
        if (crdName.isPresent()) {
            return Optional.ofNullable(k8sClient.customResourceDefinitions().withName(crdName.get()).get());
        } else {
            CustomResourceDefinitionList crdList = k8sClient.customResourceDefinitions().list();
            return crdList.getItems().stream()
                    .filter(c -> getKind(controller).equals(c.getSpec().getNames().getKind()) &&
                            getVersion(controller).equals(c.getSpec().getVersion()))
                    .findFirst();
        }
    }

    public Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> getCustomResourceClients() {
        return customResourceClients;
    }

    public void stop() {
        k8sClient.close();
    }

    public <T extends CustomResource> CustomResourceOperationsImpl getCustomResourceClients(Class<T> customResourceClass) {
        return customResourceClients.get(customResourceClass);
    }
}
