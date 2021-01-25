package io.javaoperatorsdk.operator.api.config;

import java.util.Date;

/** A class encapsulating the version information associated with this SDK instance. */
public class Version {

  private final String project;
  private final String commit;
  private final Date builtTime;

  public Version(String project, String commit, Date builtTime) {
    this.project = project;
    this.commit = commit;
    this.builtTime = builtTime;
  }

  /**
   * Returns the SDK project version
   *
   * @return the SDK project version
   */
  public String getProject() {
    return project;
  }

  /**
   * Returns the git commit id associated with this SDK instance
   *
   * @return the git commit id
   */
  public String getCommit() {
    return commit;
  }

  /**
   * Returns the date at which this SDK instance was built
   *
   * @return the build time at which this SDK instance was built or the date corresponding to {@link
   *     java.time.Instant#EPOCH} if the built time couldn't be retrieved
   */
  public Date getBuiltTime() {
    return builtTime;
  }
}
