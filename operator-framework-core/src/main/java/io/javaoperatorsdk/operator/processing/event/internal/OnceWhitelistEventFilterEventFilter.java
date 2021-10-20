package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public class OnceWhitelistEventFilterEventFilter<T extends CustomResource<?, ?>>
    implements CustomResourceEventFilter<T> {

  private static final Logger log =
      LoggerFactory.getLogger(OnceWhitelistEventFilterEventFilter.class);

  private final ConcurrentMap<CustomResourceID, CustomResourceID> whiteList =
      new ConcurrentHashMap<>();

  @Override
  public boolean acceptChange(ControllerConfiguration<T> configuration, T oldResource,
      T newResource) {
    CustomResourceID customResourceID = CustomResourceID.fromResource(newResource);
    boolean res = whiteList.remove(customResourceID, customResourceID);
    if (res) {
      log.debug("Accepting whitelisted event for CR id: {}", customResourceID);
    }
    return res;
  }

  public void whitelistNextEvent(CustomResourceID customResourceID) {
    whiteList.putIfAbsent(customResourceID, customResourceID);
  }
}
