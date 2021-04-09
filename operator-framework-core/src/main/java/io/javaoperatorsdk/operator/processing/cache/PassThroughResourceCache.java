package io.javaoperatorsdk.operator.processing.cache;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.javaoperatorsdk.operator.processing.CustomResourceCache;
import io.javaoperatorsdk.operator.processing.KubernetesResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Predicate;

public class PassThroughResourceCache {

    private static final Logger log = LoggerFactory.getLogger(PassThroughResourceCache.class);

    private ResourceCache resourceCache;
    private MixedOperation client;

    public PassThroughResourceCache(ResourceCache resourceCache, MixedOperation client) {
        this.resourceCache = resourceCache;
        this.client = client;
    }

    public void cacheResource(CustomResource resource) {
        resourceCache.cacheResource(resource);
    }

    public void cacheResource(CustomResource resource, Predicate<CustomResource> predicate) {
        // todo get + lock
        if (predicate.test(resourceCache.getLatestResource(KubernetesResourceUtils.getUID(resource)).get())) {
            log.trace("Update cache after condition is true: {}", resource);
            resourceCache.cacheResource(resource);
        }
    }

    public Optional<CustomResource> getLatestResource(String uuid) {
        return resourceCache.getLatestResource(uuid);
    }

    public void evict(String uuid) {
        resourceCache.evict(uuid);
    }
}
