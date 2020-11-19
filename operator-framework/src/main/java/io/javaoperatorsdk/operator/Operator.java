package io.javaoperatorsdk.operator;

import java.util.Arrays;

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.processing.EventDispatcher;
import io.javaoperatorsdk.operator.processing.EventScheduler;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Operator {

    private final static Logger log = LoggerFactory.getLogger(Operator.class);
    private final KubernetesClient k8sClient;
    
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
        Class<R> resClass = ControllerUtils.getCustomResourceClass(controller);
        String finalizer = ControllerUtils.getFinalizer(controller);
        MixedOperation client = k8sClient.customResources(resClass, CustomResourceList.class);
        EventDispatcher eventDispatcher = new EventDispatcher(controller,
            finalizer, new EventDispatcher.CustomResourceFacade(client), ControllerUtils.getGenerationEventProcessing(controller));
        EventScheduler eventScheduler = new EventScheduler(eventDispatcher, retry);
        registerWatches(controller, client, resClass, watchAllNamespaces, targetNamespaces, eventScheduler);
    }


    private <R extends CustomResource> void registerWatches(ResourceController<R> controller, MixedOperation client,
                                                            Class<R> resClass,
                                                            boolean watchAllNamespaces, String[] targetNamespaces, EventScheduler eventScheduler) {

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
        log.info("Registered Controller: '{}' for CRD: '{}' for namespaces: {}", controller.getClass().getSimpleName(),
                resClass, targetNamespaces.length == 0 ? "[all/client namespace]" : Arrays.toString(targetNamespaces));
    }

    private CustomResourceDefinitionContext getCustomResourceDefinitionForController(ResourceController controller) {
        String crdName = ControllerUtils.getCrdName(controller);
        CustomResourceDefinition customResourceDefinition = k8sClient.customResourceDefinitions().withName(crdName).get();
        if (customResourceDefinition == null) {
            throw new OperatorException("Cannot find Custom Resource Definition with name: " + crdName);
        }
        CustomResourceDefinitionContext context = CustomResourceDefinitionContext.fromCrd(customResourceDefinition);
        return context;
    }
}
