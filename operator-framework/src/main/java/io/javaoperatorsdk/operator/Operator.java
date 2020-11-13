package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.processing.EventDispatcher;
import io.javaoperatorsdk.operator.processing.EventScheduler;
import io.javaoperatorsdk.operator.processing.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.javaoperatorsdk.operator.ControllerUtils.*;


@SuppressWarnings("rawtypes")
public class Operator {

    private final static Logger log = LoggerFactory.getLogger(Operator.class);
    private final KubernetesClient k8sClient;
    private Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> customResourceClients = new HashMap<>();

    public Operator(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
    }


    public <R extends CustomResource> void registerControllerForAllNamespaces(ResourceController<R> controller) throws OperatorException {
        registerController(controller, true, GenericRetry.defaultLimitedExponentialRetry());
    }

    public <R extends CustomResource> void registerControllerForAllNamespaces(ResourceController<R> controller, Retry retry) throws OperatorException {
        registerController(controller, true, retry);
    }

    public <R extends CustomResource> void registerController(ResourceController<R> controller, String... targetNamespaces) throws OperatorException {
        registerController(controller, false, GenericRetry.defaultLimitedExponentialRetry(), targetNamespaces);
    }

    public <R extends CustomResource> void registerController(ResourceController<R> controller, Retry retry, String... targetNamespaces) throws OperatorException {
        registerController(controller, false, retry, targetNamespaces);
    }

    @SuppressWarnings("rawtypes")
    private <R extends CustomResource> void registerController(ResourceController<R> controller,
                                                               boolean watchAllNamespaces, Retry retry, String... targetNamespaces) throws OperatorException {
        Class<R> resClass = getCustomResourceClass(controller);
        CustomResourceDefinitionContext crd = getCustomResourceDefinitionForController(controller);
        KubernetesDeserializer.registerCustomKind(crd.getVersion(), crd.getKind(), resClass);
        String finalizer = getDefaultFinalizer(controller);
        MixedOperation client = k8sClient.customResources(crd, resClass, CustomResourceList.class, getCustomResourceDoneableClass(controller));
        EventDispatcher eventDispatcher = new EventDispatcher(controller,
                finalizer, new EventDispatcher.CustomResourceFacade(client), ControllerUtils.getGenerationEventProcessing(controller));


        ResourceCache resourceCache = new ResourceCache();
        EventScheduler eventScheduler = new EventScheduler(resourceCache, eventDispatcher, retry);
        DefaultEventSourceManager eventSourceManager = new DefaultEventSourceManager(eventScheduler);
        eventScheduler.setDefaultEventSourceManager(eventSourceManager);

        customResourceClients.put(resClass, (CustomResourceOperationsImpl) client);

        CustomResourceEventSource customResourceEventSource
                = createCustomResourceEventSource(client, resourceCache, watchAllNamespaces, targetNamespaces,
                eventScheduler, eventSourceManager);

        eventSourceManager.registerCustomResourceEventSource(customResourceEventSource);

        log.info("Registered Controller: '{}' for CRD: '{}' for namespaces: {}", controller.getClass().getSimpleName(),
                resClass, targetNamespaces.length == 0 ? "[all/client namespace]" : Arrays.toString(targetNamespaces));
    }

    private CustomResourceEventSource createCustomResourceEventSource(MixedOperation client,
                                                                      ResourceCache resourceCache,
                                                                      boolean watchAllNamespaces,
                                                                      String[] targetNamespaces,
                                                                      EventScheduler eventScheduler,
                                                                      DefaultEventSourceManager eventSourceManager) {
        CustomResourceEventSource customResourceEventSource = watchAllNamespaces ?
                CustomResourceEventSource.customResourceEventSourceForAllNamespaces(resourceCache, client) :
                CustomResourceEventSource.customResourceEventSourceForTargetNamespaces(resourceCache, client, targetNamespaces);

        customResourceEventSource.setEventHandler(eventScheduler);
        customResourceEventSource.setEventSourceManager(eventSourceManager);

        return customResourceEventSource;
    }

    private CustomResourceDefinitionContext getCustomResourceDefinitionForController(ResourceController controller) {
        String crdName = getCrdName(controller);
        CustomResourceDefinition customResourceDefinition = k8sClient.customResourceDefinitions().withName(crdName).get();
        if (customResourceDefinition == null) {
            throw new OperatorException("Cannot find Custom Resource Definition with name: " + crdName);
        }
        CustomResourceDefinitionContext context = CustomResourceDefinitionContext.fromCrd(customResourceDefinition);
        return context;
    }

    public Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> getCustomResourceClients() {
        return customResourceClients;
    }

    public <T extends CustomResource, L extends CustomResourceList<T>, D extends CustomResourceDoneable<T>> CustomResourceOperationsImpl<T, L, D>
    getCustomResourceClients(Class<T> customResourceClass) {
        return customResourceClients.get(customResourceClass);
    }

    private String getKind(CustomResourceDefinition crd) {
        return crd.getSpec().getNames().getKind();
    }

    private String getApiVersion(CustomResourceDefinition crd) {
        return crd.getSpec().getGroup() + "/" + crd.getSpec().getVersion();
    }
}
