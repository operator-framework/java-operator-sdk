/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.api.config;

import java.time.Instant;
import java.util.Date;

/** A class encapsulating the version information associated with this SDK instance. */
public class Version {

  public static final Version UNKNOWN = new Version("unknown", Date.from(Instant.EPOCH));
  private final String commit;
  private final Date builtTime;

  public Version(String commit, Date builtTime) {
    this.commit = commit;
    this.builtTime = builtTime;
  }

  /**
   * Returns the SDK project version
   *
   * @return the SDK project version
   */
  public String getSdkVersion() {
    return Versions.JOSDK;
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

  /**
   * Returns the version of the Fabric8 Kubernetes Client being used by this version of the SDK
   *
   * @return the Fabric8 Kubernetes Client version
   */
  @SuppressWarnings("unused")
  public String getKubernetesClientVersion() {
    return Versions.KUBERNETES_CLIENT;
  }
}
