package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.config.ClientConfiguration;
import io.javaoperatorsdk.operator.config.Configuration;
import io.javaoperatorsdk.operator.config.OperatorConfiguration;
import io.javaoperatorsdk.operator.processing.EventDispatcher;
import io.javaoperatorsdk.operator.processing.EventScheduler;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class Operator {
    
    private final static Logger log = LoggerFactory.getLogger(Operator.class);
    private final KubernetesClient k8sClient;
    private final Configuration config;
    private Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> customResourceClients = new HashMap<>();
    
    public Operator(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
        this.config = Configuration.defaultConfiguration();
    }
    
    public Operator() {
        this(Configuration.defaultConfiguration());
    }
    
    public Operator(Configuration config) {
        this.config = config;
        ConfigBuilder cb = new ConfigBuilder();
        
        final ClientConfiguration clientCfg = config.getClientConfiguration();
        cb.withTrustCerts(clientCfg.isTrustSelfSignedCertificates());
        if (StringUtils.isNotBlank(clientCfg.getUsername())) {
            cb.withUsername(clientCfg.getUsername());
        }
        if (StringUtils.isNotBlank(clientCfg.getPassword())) {
            cb.withUsername(clientCfg.getPassword());
        }
        if (StringUtils.isNotBlank(clientCfg.getMasterUrl())) {
            cb.withMasterUrl(clientCfg.getMasterUrl());
        }
        
        config.getOperatorConfiguration().getWatchedNamespaceIfUnique().ifPresent(cb::withNamespace);
        this.k8sClient = clientCfg.isOpenshift() ? new DefaultOpenShiftClient(cb.build()) : new DefaultKubernetesClient(cb.build());
    }
    
    public KubernetesClient getClient() {
        return k8sClient;
    }
    
    public <R extends CustomResource> void registerController(ResourceController<R> controller) throws OperatorException {
        registerController(controller, GenericRetry.defaultLimitedExponentialRetry());
    }
    
    @SuppressWarnings("rawtypes")
    public <R extends CustomResource> void registerController(ResourceController<R> controller, Retry retry) throws OperatorException {
        Class<R> resClass = ControllerUtils.getCustomResourceClass(controller);
        CustomResourceDefinitionContext crd = getCustomResourceDefinitionForController(controller);
        KubernetesDeserializer.registerCustomKind(crd.getVersion(), crd.getKind(), resClass);
        String finalizer = ControllerUtils.getFinalizer(controller);
        MixedOperation client = k8sClient.customResources(crd, resClass, CustomResourceList.class, ControllerUtils.getCustomResourceDoneableClass(controller));
        EventDispatcher eventDispatcher = new EventDispatcher(controller,
            finalizer, new EventDispatcher.CustomResourceFacade(client), ControllerUtils.getGenerationEventProcessing(controller));
        EventScheduler eventScheduler = new EventScheduler(eventDispatcher, retry);
        registerWatches(controller, client, resClass, eventScheduler);
    }
    
    
    private <R extends CustomResource> void registerWatches(ResourceController<R> controller, MixedOperation client,
                                                            Class<R> resClass,
                                                            EventScheduler eventScheduler) {
        
        CustomResourceOperationsImpl crClient = (CustomResourceOperationsImpl) client;
        final OperatorConfiguration operatorCfg = config.getOperatorConfiguration();
        final String namespaces;
        if (operatorCfg.isWatchingAllNamespaces()) {
            crClient.inAnyNamespace().watch(eventScheduler);
            namespaces = "all namespaces";
        } else if (operatorCfg.isWatchingCurrentNamespace()) {
            client.watch(eventScheduler);
            namespaces = "client namespace (" + crClient.getNamespace() + ")";
        } else {
            final Set<String> cfgNamespaces = operatorCfg.getNamespaces();
            for (String targetNamespace : cfgNamespaces) {
                crClient.inNamespace(targetNamespace).watch(eventScheduler);
                log.debug("Registered controller for namespace: {}", targetNamespace);
            }
            namespaces = String.join(", ", cfgNamespaces);
        }
        customResourceClients.put(resClass, (CustomResourceOperationsImpl) client);
        log.info("Registered Controller: '{}' for CRD: '{}' for namespaces: {}", controller.getClass().getSimpleName(),
            resClass, namespaces);
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
