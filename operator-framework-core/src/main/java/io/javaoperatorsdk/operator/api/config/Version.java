package io.javaoperatorsdk.operator.api.config;

import java.util.Date;

public class Version {
  private final String project;
  private final String commit;
  private final Date builtTime;

  public Version(String project, String commit, Date builtTime) {
    this.project = project;
    this.commit = commit;
    this.builtTime = builtTime;
  }

  public String getProject() {
    return project;
  }

  public String getCommit() {
    return commit;
  }

  public Date getBuiltTime() {
    return builtTime;
  }
}
