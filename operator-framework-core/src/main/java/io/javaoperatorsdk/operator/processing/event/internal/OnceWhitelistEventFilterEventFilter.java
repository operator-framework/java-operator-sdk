package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

public class OnceWhitelistEventFilterEventFilter<T extends CustomResource>
    implements CustomResourceEventFilter<T> {

  private ReentrantLock lock = new ReentrantLock();
  private Set<CustomResourceID> whiteList = new HashSet<>();

  @Override
  public boolean acceptChange(ControllerConfiguration<T> configuration, T oldResource,
      T newResource) {
    lock.lock();
    try {
      CustomResourceID customResourceID = CustomResourceID.fromResource(newResource);
      boolean res = whiteList.contains(customResourceID);
      cleanup(customResourceID);
      return res;
    } finally {
      lock.unlock();
    }
  }

  public void whitelistNextEvent(CustomResourceID customResourceID) {
    lock.lock();
    try {
      whiteList.add(customResourceID);
    } finally {
      lock.unlock();
    }
  }

  public void cleanup(CustomResourceID customResourceID) {
    lock.lock();
    try {
      whiteList.remove(customResourceID);
    } finally {
      lock.unlock();
    }
  }

}
