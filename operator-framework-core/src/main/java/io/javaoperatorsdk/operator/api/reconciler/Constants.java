package io.javaoperatorsdk.operator.api.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class Constants {
  // Shared object mapper across app
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


  public static final String EMPTY_STRING = "";
  public static final String WATCH_CURRENT_NAMESPACE = "JOSDK_WATCH_CURRENT";
  public static final String NO_FINALIZER = "JOSDK_NO_FINALIZER";
  public static final long NO_RECONCILIATION_MAX_INTERVAL = -1L;

  private Constants() {}
}
