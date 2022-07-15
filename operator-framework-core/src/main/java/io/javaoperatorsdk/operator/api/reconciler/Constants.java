package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Collections;
import java.util.Set;

public final class Constants {

  public static final Set<String> DEFAULT_NAMESPACES_SET =
      Collections.singleton(Constants.WATCH_ALL_NAMESPACES);
  public static final Set<String> WATCH_CURRENT_NAMESPACE_SET =
      Collections.singleton(Constants.WATCH_CURRENT_NAMESPACE);

  public static final Set<String> SAME_AS_CONTROLLER_NAMESPACES_SET =
      Collections.singleton(Constants.SAME_AS_CONTROLLER);

  public static final String NO_VALUE_SET = "";
  public static final String WATCH_CURRENT_NAMESPACE = "JOSDK_WATCH_CURRENT";
  public static final String WATCH_ALL_NAMESPACES = "JOSDK_ALL_NAMESPACES";

  public static final long NO_MAX_RECONCILIATION_INTERVAL = -1L;
  public static final String SAME_AS_CONTROLLER = "JOSDK_SAME_AS_CONTROLLER";

  public static final String RESOURCE_GVK_KEY = "josdk.resource.gvk";

  private Constants() {}
}
