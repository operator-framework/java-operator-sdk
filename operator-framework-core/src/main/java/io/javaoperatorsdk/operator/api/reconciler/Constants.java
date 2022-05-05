package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Collections;
import java.util.Set;

public final class Constants {

  public static final Set<String> DEFAULT_NAMESPACES =
      Collections.singleton(Constants.WATCH_ALL_NAMESPACES);
  public static final Set<String> WATCH_CURRENT_NAMESPACE_SET =
      Collections.singleton(Constants.WATCH_CURRENT_NAMESPACE);

  public static final String NO_VALUE_SET = "";
  public static final String WATCH_CURRENT_NAMESPACE = "JOSDK_WATCH_CURRENT";
  public static final String WATCH_ALL_NAMESPACES = "JOSDK_ALL_NAMESPACES";
  public static final long NO_RECONCILIATION_MAX_INTERVAL = -1L;

  private Constants() {}
}
