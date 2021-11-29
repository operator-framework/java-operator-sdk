package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class OnceWhitelistEventFilterEventFilter<T extends HasMetadata>
    implements ResourceEventFilter<T> {

  private static final Logger log =
      LoggerFactory.getLogger(OnceWhitelistEventFilterEventFilter.class);

  private final ConcurrentMap<ResourceID, ResourceID> whiteList = new ConcurrentHashMap<>();

  @Override
  public boolean acceptChange(ControllerConfiguration<T> configuration, T oldResource,
      T newResource) {
    ResourceID resourceID = ResourceID.fromResource(newResource);
    boolean res = whiteList.remove(resourceID, resourceID);
    if (res) {
      log.debug("Accepting whitelisted event for CR id: {}", resourceID);
    }
    return res;
  }

  public void whitelistNextEvent(ResourceID resourceID) {
    whiteList.putIfAbsent(resourceID, resourceID);
  }
}
