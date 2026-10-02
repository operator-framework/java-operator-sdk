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
package io.javaoperatorsdk.operator.performance.results;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Identifies the commit a set of results belongs to. Written as {@code run.json} next to the
 * results of a run.
 *
 * <p>The values are taken from {@code performance-tests-git.properties}, generated at build time by
 * the git-commit-id plugin, so they are also available when the results are recorded outside of
 * Maven, for example from the shaded JMH benchmark jar. Every value can be overridden with a system
 * property, which is what CI does for the branch, since a GitHub Actions checkout has a detached
 * HEAD.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunMetadata(
    String commit,
    String commitAbbrev,
    String commitTimestamp,
    String branch,
    String runTimestamp) {

  public static final String RESULTS_DIR_PROPERTY = "performance.results.dir";
  public static final String COMMIT_PROPERTY = "performance.results.commit";
  public static final String COMMIT_TIMESTAMP_PROPERTY = "performance.results.commitTimestamp";
  public static final String BRANCH_PROPERTY = "performance.results.branch";

  private static final Logger log = LoggerFactory.getLogger(RunMetadata.class);

  private static final String PROPERTIES_RESOURCE = "/performance-tests-git.properties";
  private static final String UNKNOWN = "unknown";
  private static final int ABBREV_LENGTH = 7;

  /**
   * Name of the directory the results of this run are stored in. Prefixed with the commit timestamp
   * so that runs sort chronologically without consulting the git history.
   */
  @JsonIgnore
  public String directoryName() {
    return compactTimestamp(commitTimestamp) + "-" + commitAbbrev;
  }

  public static RunMetadata current() {
    var gitProperties = loadGitProperties();

    var commit = value(COMMIT_PROPERTY, gitProperties.getProperty("git.commit.id.full"), UNKNOWN);
    var runTimestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();

    return new RunMetadata(
        commit,
        abbreviate(gitProperties.getProperty("git.commit.id.abbrev"), commit),
        value(
            COMMIT_TIMESTAMP_PROPERTY, gitProperties.getProperty("git.commit.time"), runTimestamp),
        value(BRANCH_PROPERTY, gitProperties.getProperty("git.branch"), UNKNOWN),
        runTimestamp);
  }

  private static Properties loadGitProperties() {
    var properties = new Properties();
    try (InputStream is = RunMetadata.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
      if (is == null) {
        log.warn(
            "{} not found on the classpath, results will not be associated with a commit",
            PROPERTIES_RESOURCE);
      } else {
        properties.load(is);
      }
    } catch (IOException e) {
      log.warn("Cannot read {}", PROPERTIES_RESOURCE, e);
    }
    return properties;
  }

  private static String value(String systemProperty, String fromGit, String fallback) {
    var override = System.getProperty(systemProperty);
    if (isSet(override)) {
      return override;
    }
    return isSet(fromGit) ? fromGit : fallback;
  }

  private static String abbreviate(String fromGit, String commit) {
    if (isSet(fromGit) && !isSet(System.getProperty(COMMIT_PROPERTY))) {
      return fromGit;
    }
    return commit.length() > ABBREV_LENGTH ? commit.substring(0, ABBREV_LENGTH) : commit;
  }

  private static boolean isSet(String value) {
    // the git plugin leaves unresolved placeholders in place when not run in a git checkout
    return value != null && !value.isBlank() && !value.startsWith("$");
  }

  private static String compactTimestamp(String timestamp) {
    return timestamp.replace("-", "").replace(":", "");
  }
}
