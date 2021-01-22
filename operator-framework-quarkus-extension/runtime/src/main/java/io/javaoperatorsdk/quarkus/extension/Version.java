package io.javaoperatorsdk.quarkus.extension;

import io.quarkus.runtime.annotations.RecordableConstructor;
import java.util.Date;

/** Re-publish with a recordable constructor so that quarkus can do its thing with it! */
public class Version extends io.javaoperatorsdk.operator.api.config.Version {

  @RecordableConstructor
  public Version(String project, String commit, Date builtTime) {
    super(project, commit, builtTime);
  }
}
